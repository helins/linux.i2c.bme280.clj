# BME280

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/bme280.svg)](https://clojars.org/dvlopt/bme280)

Easily interact with the popular
[BME280](https://www.bosch-sensortec.com/bst/products/all_products/bme280)
sensor via [I²C](https://en.wikipedia.org/wiki/I%C2%B2C).

Relies on [Icare](https://github.com/dvlopt/icare), an I²C clojure library.

## Usage

Read the full [API](https://dvlopt.github.io/doc/bme280.clj/index.html).

```clj
;; require lib for using I2C and lib for the sensor
(require '[icare.core :as i2c]
         '[bme280.i2c :as bme280])


;; open the I2C bus
(def bus
     (i2c/open "/dev/i2c-1"))


;; select the sensor (with proper address)
(i2c/select bus
            0x76)


;; read the api if you need a specific configuration
;; 3 registers can be configured
;; eg. normal mode, 500ms standby time, coeff 0 IIR filter,
;;     humidity oversampling = 1, temperature oversampling = 2,
;;     presure oversampling = 4
;;
;; <!> The order of these fns matters
(-> bus
    (bme280/configure 500
                      0)
    (bme280/control-humidity 1)
    (bme280/control-measurements :normal
                                 2
                                 4))


;; each sensor behave a little differently, hence it provides compensation words
;; for adjusting raw data
(def coeffs
     (bme280/coefficients (bme280/compensation-words bus)))


;; read and adjust temperature, pressure and humidity
(bme280/sensors coeffs
                (bme280/raw-data bus))
;; => {:temperature 23.3       ;; °C
;;     :pressure    960.3223   ;; Pa
;;     :humidity    46.932}    ;; %rH
```

## License

Copyright © 2017 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
