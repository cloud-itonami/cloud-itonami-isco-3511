(ns ictops.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol — proves the
  backend swap (ADR-2607011000 injection boundary) is real: the same
  sequence of operations against either backend produces the same
  observable results."
  (:require [clojure.test :refer [deftest is testing]]
            [ictops.store :as store]))

(defn- exercise [s]
  (store/register-client! s {:client-id "client-1" :name "Kobo Trade"})
  (store/register-system! s {:system-id "SYS-1" :client-id "client-1"
                              :name "payments-cluster"
                              :sla-response-minutes 30
                              :required-certifications #{"linux-admin"}})
  (store/commit-record! s {:client-id "client-1" :op :approve-incident-response
                            :system-id "SYS-1" :payload {:response-time-minutes 20}})
  (store/append-ledger! s {:disposition :commit :record {:client-id "client-1"}})
  {:client (store/client s "client-1")
   :system (store/system s "SYS-1")
   :records (store/records-of s "client-1")
   :ledger (store/ledger s)})

(deftest mem-and-datomic-parity
  (testing "same operations against MemStore and DatomicStore observe the same results"
    (let [mem (exercise (store/mem-store))
          dat (exercise (store/datomic-store))]
      (is (= "Kobo Trade" (:name (:client mem))))
      (is (= "Kobo Trade" (:name (:client dat))))
      (is (= 30 (:sla-response-minutes (:system mem))))
      (is (= 30 (:sla-response-minutes (:system dat))))
      (is (= #{"linux-admin"} (:required-certifications (:system mem))))
      (is (= #{"linux-admin"} (:required-certifications (:system dat))))
      (is (= 1 (count (:records mem))))
      (is (= 1 (count (:records dat))))
      (is (= :approve-incident-response (:op (first (:records mem)))))
      (is (= :approve-incident-response (:op (first (:records dat)))))
      (is (= 1 (count (:ledger mem))))
      (is (= 1 (count (:ledger dat)))))))

(deftest datomic-store-nil-lookups-and-empty-filter
  (testing "unregistered client/system lookups are nil, records-of on an unknown client is empty"
    (let [dat (store/datomic-store)]
      (is (nil? (store/client dat "no-such")))
      (is (nil? (store/system dat "no-such")))
      (is (empty? (store/records-of dat "no-such"))))))
