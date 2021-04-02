(ns tidb.util
  (:require [clojure.string :as str]
            [clj-http.client :as http]))

(defn isolation-level [test] (get test :isolation :repeatable-read))

(defn fail-api
  ([node port]
   (fail-api node port ""))
  ([node port failpoint]
   (str "http://" (name node) ":" port "/fail/" failpoint)))

(defn fail-enabled?
  [node port]
  (= 200 (-> (fail-api node port)
             (http/get {:throw-exceptions false})
             (:status))))

(defn fail-enable!
  [node port failpoint value]
  (-> (fail-api node port failpoint)
      (http/put {:body value})))

(defn fail-disable!
  [node port failpoint]
  (-> (fail-api node port failpoint)
      (http/delete)))

(defn fail-enable-all!
  [test port failpoints]
  (doseq [node (:nodes test)
          [failpoint value] failpoints]
    (fail-enable! node port failpoint value)))

(defn fail-enable-preset!
  [test fail-preset]
  (doseq [[kind port] {:tidb 10080 :tikv 20180}]
    (when-let [failpoints (get fail-preset kind)]
      (fail-enable-all! test port failpoints))))

(defn fail-list
  [node port]
  (->> (fail-api node port)
       (http/get)
       (:body)
       (str/split-lines)
       (map #(str/split % #"=" 2))
       (filter #(not (empty? (second %))))
       (into {})))

(def fail-presets
  {:async-commit {:tidb {"github.com/pingcap/tidb/store/tikv/prewritePrimaryFail"        "1%return"
                         "github.com/pingcap/tidb/store/tikv/prewriteSecondaryFail"      "1%return"
                         "github.com/pingcap/tidb/store/tikv/shortPessimisticLockTTL"    "5%return"
                         "github.com/pingcap/tidb/store/tikv/twoPCShortLockTTL"          "5%return"
                         "github.com/pingcap/tidb/store/tikv/commitFailedSkipCleanup"    "5%return"
                         "github.com/pingcap/tidb/store/tikv/twoPCRequestBatchSizeLimit" "5%return"
                         "github.com/pingcap/tidb/store/tikv/invalidMaxCommitTS"         "2%return"
                         "github.com/pingcap/tidb/store/tikv/asyncCommitDoNothing"       "2%return"
                         "github.com/pingcap/tidb/store/tikv/rpcFailOnSend"              "0.51%return(\"write\")"
                         "github.com/pingcap/tidb/store/tikv/rpcFailOnRecv"              "0.5%return(\"write\")"
                         "github.com/pingcap/tidb/store/tikv/noRetryOnRpcError"          "5%return(true)"
                         "github.com/pingcap/tidb/store/tikv/beforeCommit"               "2%return(\"delay\")->10%return(\"fail\")"
                         "github.com/pingcap/tidb/store/tikv/doNotKeepAlive"             "10%return"
                         "github.com/pingcap/tidb/store/tikv/snapshotGetTSAsync"         "1%sleep(100)"}
                  :tikv {"cm_after_read_key_check"         "1%sleep(100)"
                         "cm_after_read_range_check"       "1%sleep(100)"
                         "delay_update_max_ts"             "10%return"
                         "after_calculate_min_commit_ts"   "2%sleep(100)"
                         "async_commit_1pc_force_fallback" "2%return"}}})
