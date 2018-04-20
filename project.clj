(defproject dime "1.0"
  :description "Simple In-Memory database for currency conversion."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure.java-time "0.2.2"]
                 [org.clojure/tools.logging "0.3.1"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :aliases {"tests" ["with-profile" "test" "test"]}
  )
