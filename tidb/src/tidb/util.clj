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
  {:async-commit {:tidb {"github.com/pingcap/tidb/store/tikv/prewritePrimaryFail"        "0.1%return"
                         "github.com/pingcap/tidb/store/tikv/prewriteSecondaryFail"      "0.11%return"
                         "github.com/pingcap/tidb/store/tikv/shortPessimisticLockTTL"    "0.5%return"
                         "github.com/pingcap/tidb/store/tikv/twoPCShortLockTTL"          "0.5%return"
                         "github.com/pingcap/tidb/store/tikv/commitFailedSkipCleanup"    "0.3%return"
                         "github.com/pingcap/tidb/store/tikv/twoPCRequestBatchSizeLimit" "0.1%return"
                         "github.com/pingcap/tidb/store/tikv/invalidMaxCommitTS"         "0.1%return"
                         "github.com/pingcap/tidb/store/tikv/asyncCommitDoNothing"       "0.1%return"
                         "github.com/pingcap/tidb/store/tikv/rpcFailOnSend"              "0.005%return(\"write\")"
                         "github.com/pingcap/tidb/store/tikv/rpcFailOnRecv"              "0.005%return(\"write\")"
                         "github.com/pingcap/tidb/store/tikv/noRetryOnRpcError"          "0.01%return(true)"
                         "github.com/pingcap/tidb/store/tikv/beforeCommit"               "0.1%return(\"delay\")->0.1%return(\"fail\")"
                         "github.com/pingcap/tidb/store/tikv/doNotKeepAlive"             "0.1%return"
                         "github.com/pingcap/tidb/store/tikv/snapshotGetTSAsync"         "0.1%sleep(100)"}
                  :tikv {"cm_after_read_key_check"         "0.01%sleep(100)"
                         "cm_after_read_range_check"       "0.01%sleep(100)"
                         "delay_update_max_ts"             "0.01%return"
                         "after_calculate_min_commit_ts"   "0.02%sleep(100)"
                         "async_commit_1pc_force_fallback" "0.02%return"}}})
