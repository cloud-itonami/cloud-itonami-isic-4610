(ns commtrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [commtrade.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest all-four-seeded-jurisdictions-have-required-evidence
  ;; every seeded commission-brokerage jurisdiction actually has a real
  ;; required-evidence set reported honestly here
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest dual-agency-disclosure-is-a-required-evidence-item
  ;; the check with no analog in the fuel-wholesale/general-trading
  ;; siblings has its own evidence-checklist item too, mirroring how
  ;; every sibling's own defining check earns a checklist entry
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (some #(re-find #"(?i)dual-agency" %) (facts/evidence-checklist iso3))
        (str iso3 " checklist must list a dual-agency disclosure item"))))
