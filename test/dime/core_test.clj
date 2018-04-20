(ns dime.core-test
  (:require [clojure.test :refer :all]
            [dime.core :as cc]))

(def day-rate #'dime.core/day-rate)
(def convert-currency #'dime.core/convert-currency)
(def update-database #'dime.core/update-database)


(def db
  (update-database "http://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip"))

(deftest test-currency-lookup
  (testing "Rate lookups."
    (is (= (day-rate db "2015-01-01" "EUR") {:currency "EUR", :rate 1.0, :date "2015-01-01"}))
    (is (= (day-rate db "2015-01-01" "USD") {:currency "USD", :rate 1.2141, :date "2014-12-31"}))
    (is (= (day-rate db "2004-01-05" "USD") {:currency "USD", :rate 1.2657, :date "2004-01-05"}))
    (is (= (day-rate db "2005-12-05" "SGD") {:currency "SGD", :rate 1.9846, :date "2005-12-05"})))
  (testing "Invalid cases."
    (is (thrown? Exception (day-rate db "2015-01-01" "invalid")))
    (is (thrown? Exception (day-rate db "2015-01-01" nil)))
    (is (thrown? Exception (day-rate db nil "EUR")))
    (is (thrown? Exception (day-rate db "invalid" "EUR")))))

(deftest test-currency-conversion
  (testing "Convert USD to EUR."
    (is (= (convert-currency db "2016-01-05" "USD" "EUR" 10) {:value     9.31
                                                              :currency  "EUR"
                                                              :from-date "2016-01-05"
                                                              :from-rate 1.0746
                                                              :to-date   "2016-01-05"
                                                              :to-rate   1.0}))
    (is (= (convert-currency db "2004-01-05" "USD" "EUR" 10) {:value     7.9
                                                              :currency  "EUR"
                                                              :from-date "2004-01-05"
                                                              :from-rate 1.2657
                                                              :to-date   "2004-01-05"
                                                              :to-rate   1.0})))

  (testing "Convert other currencies."
    (is (= (convert-currency db "2005-12-05" "SGD" "USD" 10) {:value     5.93
                                                              :currency  "USD"
                                                              :from-date "2005-12-05"
                                                              :from-rate 1.9846
                                                              :to-date   "2005-12-05"
                                                              :to-rate   1.1767}))
    (is (= (convert-currency db "2007-07-05" "JPY" "SGD" 10) {:value     0.12
                                                              :currency  "SGD"
                                                              :from-date "2007-07-05"
                                                              :from-rate 167.1
                                                              :to-date   "2007-07-05"
                                                              :to-rate   2.0718})))

  (testing "Holidays, weekends and discontinued currencies the latest known rate should be used."
    ;holiday
    (is (= (convert-currency db "2016-01-01" "JPY" "SGD" 10) {:value     0.12
                                                              :currency  "SGD"
                                                              :from-date "2015-12-31"
                                                              :from-rate 131.07
                                                              :to-date   "2015-12-31"
                                                              :to-rate   1.5417}))
    ;saturday
    (is (= (convert-currency db "2016-12-03" "USD" "SGD" 10) {:value     14.23
                                                              :currency  "SGD"
                                                              :from-date "2016-12-02"
                                                              :from-rate 1.0642
                                                              :to-date   "2016-12-02"
                                                              :to-rate   1.514}))
    ;sunday
    (is (= (convert-currency db "2016-12-04" "USD" "SGD" 10) {:value     14.23
                                                              :currency  "SGD"
                                                              :from-date "2016-12-02"
                                                              :from-rate 1.0642
                                                              :to-date   "2016-12-02"
                                                              :to-rate   1.514}))

    ; Cyprus Pound (CYP) finished in December 2007 in this case we use the latest known rate.
    (is (= (convert-currency db "2016-12-05" "USD" "CYP" 10) {:value     5.47
                                                              :currency  "CYP"
                                                              :from-date "2016-12-05"
                                                              :from-rate 1.0702
                                                              :to-date   "2007-12-31"
                                                              :to-rate   0.585274})))

  (testing "Convert same currencies."
    (is (= (convert-currency db "2015-01-01" "EUR" "EUR" 10) {:value     10.0
                                                              :currency  "EUR"
                                                              :from-date "2015-01-01"
                                                              :from-rate 1.0
                                                              :to-date   "2015-01-01"
                                                              :to-rate   1.0}))
    (is (= (convert-currency db "2015-01-01" "USD" "USD" 10) {:value     10.0
                                                              :currency  "USD"
                                                              :from-date "2014-12-31"
                                                              :from-rate 1.2141
                                                              :to-date   "2014-12-31"
                                                              :to-rate   1.2141})))
  (testing "Test invalid cases."
    (is (thrown? Exception (convert-currency db "2015-01-01" "11" "EUR" 10)))
    (is (thrown? Exception (convert-currency db "2015-01-01" nil "EUR" 10)))
    (is (thrown? Exception (convert-currency db "2015-01-01" "EUR" "11" 10)))
    (is (thrown? Exception (convert-currency db "2015-01-01" "EUR" nil 10)))
    (is (thrown? Exception (convert-currency db nil "EUR" "USD" 10)))
    (is (thrown? Exception (convert-currency db "invalid" "EUR" "USD" 10)))
    (is (thrown? Exception (convert-currency db "2015-01-01" "EUR" "USD" "")))
    (is (thrown? Exception (convert-currency db "2015-01-01" "EUR" "USD" nil)))))

(deftest test-currency-exists
  (testing "Valid cases"
    (let [currencies ["CHF" "SGD" "GBP" "CYP" "MYR" "CZK" "LVL" "HKD" "PLN" "TRY" "JPY" "INR" "SEK" "IDR" "SKK" "KRW"
                      "THB" "NZD" "USD" "CAD" "ILS" "CNY" "MXN" "ROL" "MTL" "RUB" "SIT" "RON" "NOK" "ISK" "HRK" "PHP"
                      "HUF" "LTL" "TRL" "AUD" "BRL" "DKK" "ZAR" "BGN" "EEK" "EUR"]]
      (doseq [currency-item currencies]
        (is (true? (cc/exists? currency-item)))
        (is (true? (cc/exists? (keyword currency-item)))))))
  (testing "Invalid cases."
    (is (false? (cc/exists? "")))
    (is (false? (cc/exists? nil)))
    (is (false? (cc/exists? [])))
    (is (false? (cc/exists? {})))
    (is (false? (cc/exists? 1)))
    (is (false? (cc/exists? "NOTEXISTS")))
    (is (false? (cc/exists? :NOTEXISTS)))))
