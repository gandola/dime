(ns dime.core
  (require
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [java-time :as time])
  (:import (java.util.zip ZipInputStream)
           (java.util Collections TimeZone)
           (java.lang Math)
           (java.text SimpleDateFormat)))

(def ^:const comma-regex #",")

(def ^:private date-comparator
  (comparator (fn [x y]
                (neg? (compare y x)))))

(def ^:private ^ThreadLocal thread-local-date
  ;; SimpleDateFormat is not thread-safe, so we use a ThreadLocal proxy for access.
  (proxy [ThreadLocal] []
    (initialValue []
      (doto (SimpleDateFormat. "yyyy-MM-dd")
        (.setTimeZone (TimeZone/getTimeZone "UTC"))
        (.setLenient false)))))

(defn ^:private valid-date
  "Validates the given date string."
  [date-str]
  ; in this case we need the date to follow strictly the format "yyyy-MM-dd"
  (and (= (count date-str) 10)
       (try
         (.parse (.get thread-local-date) date-str)
         true
         (catch Exception _ false))))

(defn ^:private quite-double-parse
  "Returns the parsed double, nil otherwise."
  [value-str]
  (try
    (Double/parseDouble value-str)
    (catch Exception _)))

(defn ^:private update-database
  [filename]
  (log/info "Updating the currency database...")
  (with-open [stream (ZipInputStream. (io/input-stream filename))]
    (.getNextEntry stream)
    (with-open [in (io/reader stream)]
      (let [counter (atom 0)
            header (atom nil)
            data (atom (sorted-map))]
        ;process file line by line
        (doseq [line (line-seq in)]
          (let [line-parts (clojure.string/split line comma-regex)
                date-str (get line-parts 0)]
            (if (zero? @counter)
              ; process the header
              (compare-and-set! header nil (map keyword line-parts))
              ; process lines
              (compare-and-set! data
                                @data
                                (reduce-kv
                                  (fn [m k v]
                                    (let [rate (quite-double-parse v)]
                                      ; if there is a valid rate then let's index it, otherwise ignore it.
                                      (if (some? rate)
                                        (assoc m k (if (nil? (get m k))
                                                     (sorted-map date-str rate)
                                                     (assoc (get m k) date-str rate)))
                                        m)))
                                  @data (zipmap @header line-parts)))))
          (swap! counter inc))
        (log/info "Updated the currency database with:" @counter "entries.")
        (into {} (map
                   (fn [entry]
                     {(first entry) {:data  (second entry)
                                     :index (reverse (keys (second entry)))}})
                   @data))))))

(defn ^:private day-rate
  "Returns the currency rate for the given date to the given currency.
   If the rate for a specific day does not exist for the given currency it will return the latest known rate.
   If the currency does not exist it will throw an exception."
  [db date-str currency]
  (when (false? (valid-date date-str))
    (throw (Exception. (str "Invalid date format (use strictly the format yyyy-MM-dd): " date-str))))
  (if (= currency "EUR")
    {:currency currency
     :rate     1.0
     :date     date-str}
    (let [currency-data (get db (keyword currency))
          idx (Collections/binarySearch (or (:index currency-data) [])
                                        date-str
                                        date-comparator)
          index (if (neg? idx) (dec (Math/abs idx)) idx)]

      (if (pos? (count (:index currency-data)))
        {:currency currency
         :rate     (get (:data currency-data) (nth (:index currency-data) index))
         :date     (nth (:index currency-data) index)}
        (throw (Exception. (str "Unable to find data for the currency: " currency " for day: " date-str)))))))

(defn ^:private convert-currency
  "Converts the given value from the given 'from' currency to the 'to' currency using the given date rates."
  [db date-str from-currency-str to-currency-str value]
  (when-not (number? value)
    (throw (Exception. (str "Invalid value type: " (type value)))))
  (let [from-currency (day-rate db date-str from-currency-str)
        to-currency (day-rate db date-str to-currency-str)]
    {:value     (double (/ (Math/round (double (* (/ value (:rate from-currency)) (:rate to-currency) 100))) 100))
     :currency  to-currency-str
     :from-date (:date from-currency)
     :from-rate (:rate from-currency)
     :to-date   (:date to-currency)
     :to-rate   (:rate to-currency)
     }))

;;;;;
;;;;; Database is updated everyday.
;;;;;
(def ^:private database (atom nil))

(defn ^:private ensure-database []
  (swap! database (fn [db]
                    (let [today-midnight (java-time/truncate-to (java-time/instant) :days)]
                      (if (or (nil? db)
                              (time/after? today-midnight (:last-update @db)))
                        ;delay to ensure that we have thread-safety...
                        (delay {:last-update today-midnight
                                :data        (update-database "http://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip")})
                        db)))))

(defn convert
  [date from-currency-str to-currency-str value]
  (let [date-str (if (string? date) date (.format (.get thread-local-date) date))]
    (ensure-database)
    (convert-currency (:data @@database) date-str from-currency-str to-currency-str value)))

(defn exists?
  "Checks if the given currency is a supported currency for rate conversion or not."
  [currency]
  (ensure-database)
  (cond
    (or (= currency "EUR") (= currency :EUR)) true          ; EUR is not included on this database because it is the indexed currency.
    (keyword? currency) (some? (get (:data @@database) currency))
    (and (string? currency) (not (clojure.string/blank? currency))) (some? (get (:data @@database) (keyword currency)))
    :else false))

(defn rate
  "Gets the rate for a specific day."
  [date currency]
  (ensure-database)
  (day-rate (:data @@database) date currency))