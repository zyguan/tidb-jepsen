(ns tidb.sql
  (:require [clojure.string :as str]
            [dom-top.core :as dt]
            [jepsen [util :as util :refer [default timeout]]]
            [clojure.java.jdbc :as j]
            [clojure.tools.logging :refer [info warn]]
            [cheshire.core :as json]
            [slingshot.slingshot :refer [try+ throw+]]))

(def txn-timeout     5000)
(def connect-timeout 10000)
(def socket-timeout  10000)
(def open-timeout
  "How long will we wait for an open call by default"
  5000)

(defn conn-spec
  "jdbc connection spec for a node."
  [node]
  {:classname       "org.mariadb.jdbc.Driver"
   :subprotocol     "mariadb"
   :subname         (str "//" (name node) ":4000/test")
   :user            "root"
   :password        ""
   :connectTimeout  connect-timeout
   :socketTimeout   socket-timeout})

(defn txn-mode
  [test]
  (let [mode (:txn-mode test "pessimistic")]
    (if (= "mixed" mode) (rand-nth ["pessimistic" "optimistic"]) mode)))

(defn init-sql
  [test]
  (cond-> (:init-sql test)
          (not= :default (:auto-retry test)) (conj (str "set @@tidb_disable_txn_auto_retry = " (if (:auto-retry test) 0 1)))
          (not= :default (:auto-retry-limit test)) (conj (str "set @@tidb_retry_limit = " (:auto-retry-limit test 10)))
          (:follower-read test) (conj "set @@tidb_replica_read = 'follower'")
          true (conj (str "set @@tidb_txn_mode = '" (txn-mode test) "'"))
          true (conj "set @@tidb_general_log = 1")))

(defn init-conn!
  "Sets initial variables on a connection, based on test options.
  Returns conn."
  [conn test]
  (doseq [stmt (init-sql test)]
    (info (str "init> " stmt))
    (j/execute! conn [stmt]))
  conn)

(defn open
  "Opens a connection to the given node."
  ([node test]
   (open node test open-timeout))
  ([node test open-timeout]
   (timeout open-timeout
            (throw+ {:type :connect-timed-out
                     :node node})
            (util/retry 1
                        (try
                          (let [spec (assoc (conn-spec node)
                                            ::node node
                                            ; jdbc is gonna convert everything
                                            ; in the spec to a string at some
                                            ; point, so we scrupulously do NOT
                                            ; want to pass it the full test, or
                                            ; it'll blow out RAM.
                                            ::test (select-keys
                                                     test
                                                     [:auto-retry
                                                      :auto-retry-limit]))
                                conn   (j/get-connection spec)
                                spec'  (j/add-connection spec conn)]
                            (assert spec')
                            (init-conn! spec' test))
                          (catch java.sql.SQLNonTransientConnectionException e
                            ; Conn refused
                            (throw e))
                          (catch Throwable t
                            (info t "Unexpected connection error, retrying")
                            (throw t)))))))

(defn close!
  "Given a JDBC connection, closes it and returns the underlying spec."
  [conn]
  (when-let [c (j/db-find-connection conn)]
    (.close c))
  (dissoc conn :connection))

(defn reopen!
  "Closes a connection and returns a new one based on the given connection."
  [conn]
  (close! conn)
  (open (::node conn) (::test conn)))

(defn execute!
  "Like j/execute!, but provides a default timeout."
  ([db sql-params]
   (execute! db sql-params {}))
  ([db sql-params opts]
   (j/execute! db sql-params (default opts :timeout (/ txn-timeout 1000)))))

(defn query
  "Like j/query, but provides a default timeout."
  ([db sql-params]
   (query db sql-params {}))
  ([db sql-params opts]
   (j/query db sql-params (default opts :timeout (/ txn-timeout 1000)))))

(defn update!
  "Like j/update, but provides a default timeout."
  ([db table set-map where-clause]
   (update! db table set-map where-clause {}))
  ([db table set-map where-clause opts]
   (j/update! db table set-map where-clause (default opts :timeout (/ txn-timeout 1000)))))

(defn insert!
  "Like j/insert!, but provides a default timeout."
  ([db table row]
   (insert! db table row {}))
  ([db table row opts]
   (j/insert! db table row (default opts :timeout (/ txn-timeout 1000)))))

(defmacro with-conn-failure-retry
 "TiDB tends to be flaky for a few seconds after starting up, which can wind
  up breaking our setup code. This macro adds a little bit of backoff and retry
  for those conditions."
 [conn & body]
 (assert (symbol? conn))
 (let [tries    (gensym 'tries) ; try count
       e        (gensym 'e)     ; errors
       conn-sym (gensym 'conn)  ; local conn reference
       retry `(do (when (zero? ~tries)
                    (info "Out of retries!")
                    (throw ~e))
                  (info "Connection failure; retrying...")
                  (Thread/sleep (rand-int 2000))
                  (~'retry (reopen! ~conn-sym) (dec ~tries)))]
 `(dt/with-retry [~conn-sym ~conn
                  ~tries    32]
    (let [~conn ~conn-sym] ; Rebind the conn symbol to our current connection
      ~@body)
    (catch java.sql.BatchUpdateException ~e ~retry)
    (catch java.sql.SQLTimeoutException ~e ~retry)
    (catch java.sql.SQLNonTransientConnectionException ~e ~retry)
    (catch java.sql.SQLException ~e
      (condp re-find (.getMessage ~e)
        #"Resolve lock timeout"           ~retry ; high contention
        #"Information schema is changed"  ~retry ; ???
        #"called on closed connection"    ~retry ; definitely didn't happen
        #"Region is unavailable"          ~retry ; okay fine
        (do (info "with-conn-failure-retry isn't sure how to handle SQLException with message" (pr-str (class (.getMessage ~e))) (pr-str (.getMessage ~e)))
            (throw ~e)))))))

(def await-id
  "Used to generate unique identifiers for awaiting cluster stabilization"
  (atom 0))

(defn await-node
  "Waits for a node to become ready by opening a connection, creating a table,
  and inserting a record."
  [node]
  (info "Waiting for" node)
  ; Give it 30 seconds to open a connection
  (if (let [c (open node {} 30000)]
        (try
          ; And however long it takes to run these
          (with-conn-failure-retry c
            (j/execute! c ["create table if not exists jepsen_await
                           (id int primary key, val int)"]))
            (j/insert! c "jepsen_await" {:id  (swap! await-id inc)
                                         :val (rand-int 5)})
          true
          (finally
            (close! c))))
    (info node "ready")
    (recur node)))

(def rollback-msg
  "mariadb drivers have a few exception classes that use this message"
  "Deadlock found when trying to get lock; try restarting transaction")

(defmacro capture-txn-abort
  "Converts aborted transactions to an ::abort keyword"
  [& body]
  `(try ~@body
        (catch java.sql.SQLTransactionRollbackException e#
          (if (str/ends-with? (.getMessage e#) rollback-msg)
            ::abort
            (throw e#)))
        (catch java.sql.BatchUpdateException e#
          (if (str/ends-with? (.getMessage e#) rollback-msg)
            ::abort
            (throw e#)))
        (catch java.sql.SQLException e#
          (condp re-find (.getMessage e#)
            #"can not retry select for update statement" ::abort
            #"\[try again later\]" ::abort
            (throw e#)))))

(defmacro with-txn-retries
  "Retries body on rollbacks."
  [& body]
  `(loop []
     (let [res# (capture-txn-abort ~@body)]
       (if (= ::abort res#)
         (recur)
         res#))))

(defmacro with-txn-aborts
  "Aborts body on rollbacks."
  [op & body]
  `(let [res# (capture-txn-abort ~@body)]
     (if (= ::abort res#)
       (assoc ~op :type :fail, :error :conflict)
       res#)))

(defmacro with-error-handling
  "Common error handling for errors, including txn aborts."
  [op & body]
  `(try
    (with-txn-aborts ~op ~@body)

    (catch java.sql.BatchUpdateException e#
      (condp re-find (.getMessage e#)
        #"Query timed out" (assoc ~op :type :info, :error :query-timed-out)
        (throw e#)))

    (catch java.sql.SQLNonTransientConnectionException e#
      (condp re-find (.getMessage e#)
        #"Connection timed out" (assoc ~op :type :info, :error :conn-timed-out)
        (throw e#)))

    (catch clojure.lang.ExceptionInfo e#
      (cond (= "Connection is closed" (.cause (:rollback (ex-data e#))))
            (assoc ~op :type :info, :error :conn-closed-rollback-failed)

            (= "createStatement() is called on closed connection"
               (.cause (:rollback (ex-data e#))))
            (assoc ~op :type :fail, :error :conn-closed-rollback-failed)

            true (do (info e# :caught (pr-str (ex-data e#)))
                     (info :caught-rollback (:rollback (ex-data e#)))
                     (info :caught-cause    (.cause (:rollback (ex-data e#))))
                     (throw e#))))))

(defmacro with-txn
  "Executes body in a transaction, with a timeout, automatically retrying
  conflicts and handling common errors."
  [op opts & body]
  `(timeout (+ 1000 socket-timeout) (assoc ~op :type :info, :error :timed-out)
            (with-error-handling ~op
              (let [op# (with-txn-retries
                          (when-let [opts# (first [~@(rest (rest opts))])]
                            (when-let [before-hook# (:before-hook opts#)]
                              (before-hook#)))
                          (j/with-db-transaction ~opts
                            (attach-start-ts ~(first opts) (do ~@body))))]
                (attach-commit-ts ~(second opts) op#)))))

(defmacro with-conn
  [[c node] & body]
  `(j/with-db-connection [~c (conn-spec ~node)]
     (when (.isClosed (j/db-find-connection ~c))
       (Thread/sleep 1000)
       (throw (ex-info "Connection not yet ready."
                       {:type :conn-not-ready})))
     ~@body))

(defn create-index!
  "proxies to j/execute!, but catches \"index already exist\" errors
  transparently."
  [& args]
  (try (apply j/execute! args)
       (catch java.sql.SQLSyntaxErrorException e
         (when-not (re-find #"index already exist" (.getMessage e))
           (throw e)))))

(defn rand-init-txn!
  [test conn]
  (let [stmts (:init-txn-sql test)
        stmt (when-not (empty? stmts) (rand-nth stmts))]
    (when stmt
      (info (str "init-txn> " stmt))
      (execute! conn [stmt] {:transaction? false}))))

(defn query-txn-info
  [conn]
  (-> (query conn ["select @@tidb_last_txn_info info"])
      (first)
      (:info)
      (#(if (empty? %) "{\"start_ts\":0,\"commit_ts\":0}" %))
      (json/parse-string true)))

(defn attach-start-ts
  [conn op]
  (try
    (let [start_ts (first (query conn ["select @@tidb_current_ts ts"] {:row-fn #(Long. (:ts %))}))]
      (if (pos? start_ts) (assoc op :txn-info {:start_ts start_ts}) op))
    (catch Exception e
      (do (info "failed to obtain start-ts:" (.getMessage e)) op))))

(defn attach-commit-ts
  [conn op]
  (try
    (if-let [start_ts (get-in op [:txn-info :start_ts])]
      (let [txn-info (query-txn-info conn)]
        (if (= start_ts (:start_ts txn-info))
            (assoc op :txn-info info)
            op))
      op)
    (catch Exception e
      (do (info "failed to obtain commit-ts:" (.getMessage e)) op))))

(defn attach-txn-info
  [conn op]
  (try
    (let [txn-info (query-txn-info conn)]
      (assoc op :txn-info txn-info))
    (catch Exception e
      (do (info "failed to obtain txn-info:" (.getMessage e)) op))))
