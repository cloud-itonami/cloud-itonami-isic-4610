(ns commtrade.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [commtrade.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/brokerage-deal s "bd-1"))))
      (is (= "Northgate Steel Buyers KK" (:principal-buyer (store/brokerage-deal s "bd-1"))))
      (is (= "Sendai Rolling Mills Co" (:principal-seller (store/brokerage-deal s "bd-1"))))
      (is (= :seller-side (:mandate-side (store/brokerage-deal s "bd-1"))))
      (is (= "ATL" (:jurisdiction (store/brokerage-deal s "bd-2"))))
      (is (nil? (:mandate-terms (store/brokerage-deal s "bd-3"))) "bd-3 no mandate-terms")
      (is (false? (:buyer-kyc-cleared? (store/brokerage-deal s "bd-4"))) "bd-4 buyer KYC not cleared")
      (is (false? (:seller-sanctions-screened? (store/brokerage-deal s "bd-5"))) "bd-5 seller sanctions not screened")
      (is (= :dual (:mandate-side (store/brokerage-deal s "bd-6"))) "bd-6 dual mandate")
      (is (false? (:dual-agency-disclosed? (store/brokerage-deal s "bd-6"))) "bd-6 dual agency not disclosed")
      (is (false? (:confirmed? (store/brokerage-deal s "bd-1"))))
      (is (false? (:invoiced? (store/brokerage-deal s "bd-1"))))
      (is (= ["bd-1" "bd-2" "bd-3" "bd-4" "bd-5" "bd-6"]
             (mapv :id (store/all-brokerage-deals s))))
      (is (nil? (store/assessment-of s "bd-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/confirmation-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-confirmation-sequence s "JPN")))
      (is (zero? (store/next-invoice-sequence s "JPN")))
      (is (false? (store/deal-already-confirmed? s "bd-1")))
      (is (false? (store/deal-already-invoiced? s "bd-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :mandate/upsert
                                 :value {:id "bd-1" :principal-seller "Sendai Rolling Mills Co"}})
        (is (= "Sendai Rolling Mills Co" (:principal-seller (store/brokerage-deal s "bd-1"))))
        (is (= "JPN" (:jurisdiction (store/brokerage-deal s "bd-1"))) "unrelated field preserved"))
      (testing "mandate-assessment payloads commit and read back"
        (store/commit-record! s {:effect :mandate-assessment/set :path ["bd-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "bd-1"))))
      (testing "deal confirmation drafts a record and advances the confirmation sequence"
        (store/commit-record! s {:effect :deal/mark-confirmed :path ["bd-1"]})
        (is (= "JPN-CONFIRM-000000" (get (first (store/confirmation-history s)) "record_id")))
        (is (= "deal-confirmation-draft" (get (first (store/confirmation-history s)) "kind")))
        (is (true? (:confirmed? (store/brokerage-deal s "bd-1"))))
        (is (= 1 (count (store/confirmation-history s))))
        (is (= 1 (store/next-confirmation-sequence s "JPN")))
        (is (true? (store/deal-already-confirmed? s "bd-1"))))
      (testing "commission-invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :deal/mark-invoiced :path ["bd-1"]})
        (is (= "JPN-COMMISSION-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "commission-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/brokerage-deal s "bd-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "JPN")))
        (is (true? (store/deal-already-invoiced? s "bd-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/brokerage-deal s "nope")))
    (is (= [] (store/all-brokerage-deals s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/confirmation-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-confirmation-sequence s "JPN")))
    (is (zero? (store/next-invoice-sequence s "JPN")))
    (store/with-brokerage-deals s {"x" {:id "x" :deal-id "BD-X" :subject-matter "Steel coil, 5000 MT"
                                        :principal-buyer "b" :principal-seller "s"
                                        :commission-rate "2%"
                                        :mandate-side :seller-side :mandate-terms "engagement letter"
                                        :buyer-kyc-cleared? true :seller-kyc-cleared? true
                                        :buyer-sanctions-screened? true :seller-sanctions-screened? true
                                        :dual-agency-disclosed? false
                                        :confirmed? false :invoiced? false
                                        :jurisdiction "JPN" :status :intake
                                        :confirmation-number nil :invoice-number nil}})
    (is (= "b" (:principal-buyer (store/brokerage-deal s "x"))))
    (is (= :seller-side (:mandate-side (store/brokerage-deal s "x"))))))
