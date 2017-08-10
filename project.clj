(defproject bme280
            "0.0.0-alpha0"

  :description  "<!> Developer is too lazy to write a description"
  :url          "Missing, maybe there is no website yet ?"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins      [[lein-codox "0.10.3"]]
  :codox        {:output-path "doc/auto"}
  :main         bme280.core
  :dependencies [[org.clojure/clojure    "1.9.0-alpha17"]
                 [org.clojure/spec.alpha "0.1.123"]
                 [dvlopt/pi4clj          "0.0.0-alpha3"]]
  :profiles     {:dev     {:source-paths ["dev"]
                           :main         user
                           :plugins      [[venantius/ultra "0.5.1"]
                                          [lein-midje      "3.0.0"]]
                           :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                          [org.clojure/test.check "0.9.0"]
                                          [criterium "0.4.4"]]
                           :global-vars  {*warn-on-reflection* true}}
                 :uberjar {:aot :all}})
