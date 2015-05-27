(ns samsara-core.core
  (:require [moebius.core :refer :all])
  (:require [digest :refer [sha-256]]))


(defenrich inject-kibana-timestamp
  #_"Adds the `ts` attribute suitable for Kibana4"
  [{:keys [timestamp] :as event}]
  (inject-as event :ts (java.util.Date. timestamp)))



(defenrich is-timestamp-reliable
  #_"It verifies if the client timestamp seems reliable,
   and if it is it will inject and attribute `timeReliable`
   true/false."
  [{:keys [timestamp receivedAt publishedAt] :as event}]
  (when (and timestamp receivedAt publishedAt)
        (assoc event :timeReliable
               (cond
                ;; is the timestamp coming from
                ;; the future
                (> timestamp publishedAt)  false
                (> publishedAt receivedAt) false
                ;; is received - published within
                ;; a reasonable time (10s) otherwise is bogus
                (not (>= 10000 (- receivedAt publishedAt) 0)) false
                ;; is the event generated within a
                ;; reasonable time? 10d otherwise is bogus
                (not (>= (* 10 24 60 60 1000) (- publishedAt timestamp) 0)) false
                :else true))))



(defenrich inject-id
  #_"It inject a consistent and repeatable ID is not already provided"
  [{:keys [timestamp eventName sourceId id] :as event}]
  (when-not id
    (inject-as event :id (sha-256 (str timestamp "/" eventName "/" sourceId)))))



(defn- index-name
  "returns the index name based where the event should land"
  [{:keys [strategy base-index] :as indexing-strategy} {:keys [receivedAt] :as event}]
  (case (keyword strategy)
    :daily (format "%s-%tF" base-index (java.util.Date. receivedAt))
    base-index))



(defn format-to-qanal
  "It formats the event into the qanal format"
  [{:keys [strategy base-index event-type] :as indexing-strategy}]
  (enricher
   (fn [{:keys [receivedAt id] :as event}]
     {:index (index-name indexing-strategy event)
      :type event-type
      :id id
      :source event})))



(defn samsara-pipeline [indexing-strategy]
  (pipeline
   inject-kibana-timestamp
   is-timestamp-reliable
   inject-id
   (format-to-qanal indexing-strategy)))



(defn samsara-processor [indexing-strategy]
  (moebius (samsara-pipeline indexing-strategy)))
