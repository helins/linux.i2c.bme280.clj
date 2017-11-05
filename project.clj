(defproject dvlopt/bme280
            "0.0.0-alpha2"

  :description  "Clojure lib for interacting with a BME280 sensor via I2C"
  :url          "https://github.com/dvlopt/bme280.clj"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[dvlopt/icare "0.0.0-alpha0"]]
  :profiles     {:dev {:source-paths ["dev"]
                       :main         user
                       :dependencies [[org.clojure/clojure         "1.9.0-alpha20"]
                                      [org.clojure/spec.alpha      "0.1.123"]
                                      [org.clojure/tools.namespace "0.2.11"]
                                      [org.clojure/test.check      "0.9.0"]
                                      [criterium                   "0.4.4"]]
                       :plugins      [[venantius/ultra "0.5.1"]
                                      [lein-midje      "3.0.0"]
                                      [lein-codox      "0.10.3"]]
                       :codox        {:output-path  "doc/auto"
                                      :source-paths ["src"]}
                       :repl-options {:timeout 180000}
                       :global-vars  {*warn-on-reflection* true}}})
