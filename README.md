# linux.i2c.bme280

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/linux.i2c.bme280.svg)](https://clojars.org/dvlopt/linux.i2c.bme280)

Interact with the popular
[BME280](https://www.bosch-sensortec.com/bst/products/all_products/bme280)
sensor via [I2C](https://en.wikipedia.org/wiki/I%C2%B2C) from clojure.

Relies on [dvlopt/linux.i2c](https://github.com/dvlopt/linux.i2c.clj) for
performing I2C operations.

## Usage

Read the [API](dvlopt.github.io/doc/clojure/dvlopt/linux.i2c.bme280/index.html).

In short, without error handling :

```clj
(require '[dvlopt.linux.i2c        :as i2c]
         '[dvlopt.linux.i2c.bme280 :as bme280])


(with-open [^java.lang.AutoCloseable bus (i2c/bus "/dev/i2c-1")]

  (i2c/select-slave bus
                    0x76)

  (let [;; Those compensation words will be used for adjusting raw data received from sensors.
        compensation-words (bme280/compensation-words bus)

        ;; Configuration map.
        config             {::bme280/iir-filter               16
                            ::bme280/mode                     :forced
                            ::bme280/oversampling.humidity    :x1
                            ::bme280/oversampling.pressure    :x2
                            ::bme280/oversampling.temperature :x4}

        ;; Computes how many milliseconds a single measurement takes, at most.
        wait-ms            (:maximum (bme280/cycle-duration config))]
    (bme280/configure bus
                      config)
    (Thread/sleep wait-ms)
    (bme280/read-sensors bus
                         compensation-words)

    => {::bme280/humidity    46.932
        ::bme280/pressure    960.3226
        ::bme280/temperature 23.3}
    ))
```

## License

Copyright © 2017 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
