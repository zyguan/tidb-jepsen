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

(defn fail-list
  [node port]
  (->> (fail-api node port)
       (http/get)
       (:body)
       (str/split-lines)
       (map #(str/split % #"=" 2))
       (filter #(not (empty? (second %))))
       (into {})))
