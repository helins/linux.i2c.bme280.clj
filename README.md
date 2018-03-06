# BME280

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/i2c.bme280.svg)](https://clojars.org/dvlopt/i2c.bme280)

Interact with the popular
[BME280](https://www.bosch-sensortec.com/bst/products/all_products/bme280)
sensor via [I2C](https://en.wikipedia.org/wiki/I%C2%B2C) from clojure.

IO libraries often enforce specific behaviors such as error checking. The user
is sometimes disconnected from what is happening. For instance, it is often
unclear what exactly failed when an error occurs. Sometimes those libraries log
what is happening, sometimes not.

Rather, this API aims to ressemble the datasheet. It offers specs, values and
functions for processing and computing what is needed but do not perform IO
operations. This approach means there is a bit more boilerplate but at least,
    the user has the freedom to handle things as suited.

We recommend using [dvlopt.i2c](https://github.com/dvlopt/i2c) for performing
I2C operations.


## Usage

Read the [API](https://dvlopt.github.io/doc/dvlopt/i2c.bme280/).

The `dvlopt.i2c.bme280/io` map describes IO operations and holds useful
information such as registers.

Using [dvlopt.i2c](https://github.com/dvlopt/i2c) (without error checking) :

```clj
(require '[dvlopt.i2c        :as i2c]
         '[dvlopt.i2c.bme280 :as bme280])


;; First, we need to open the I2C bus.
(def bus
     (::i2c/bus (i2c/open "/dev/i2c-1")))


;; Then, select the sensor (with the proper address).
(i2c/select bus
            0x76)


;; We need to configure our device as needed.
;; The easiest way is to use the high level function `prepare` which tells us what
;; we have to do.
(let [preparation (bme280/prepare {::bme280/iir-filter               0
                                   ::bme280/mode                     :forced
                                   ::bme280/oversampling.humidity    :x1
                                   ::bme280/oversampling.pressure    :x2
                                   ::bme280/oversampling.temperature :x4
                                   ::bme280/standby                  :1000-ms})]
 ;; Let's write the various configuration bytes to the proper registers.
 ;; Attention, order matters.
 (doseq [{register ::bme280/register
          ubyte    ::bme280/ubyte}   (::bme280/configuration.writes preparation)]
   (i2c/write-byte bus
                   register
                   ubyte))
 ;; Let's sleep util the measure completes.
 (Thread/sleep (::bme280/duration.max preparation)))


;; We need to read and prepare compensation words for adjusting sensor data later on.
(def cw
     (bme280/compensation-words (let [cmd (:compensation-words bme280/io)
                                      ba  (byte-array (::bme280/length cmd))]
                                 (doseq [{index    ::bme280/index
                                          length   ::bme280/length
                                          register ::bme280/register} (::bme280/registers cmd)]
                                   (i2c/read-bytes bus
                                                   register
                                                   ba
                                                   index
                                                   length)))))


;; Everything is ready for reading the sensors when needed.
(def buffer
     (byte-array (::length (:data bme280/io))))


(i2c/read-bytes bus
                (::bme280/register (:data bme280/io))
                buffer)


;; And finally processing this data.
(bme280/data buffer
             cw)

;; => {::bme280/humidity    46.932   ;; %rH  
;;     ::bme280/pressure    960.3226 ;; Pa
;;     ::bme280/temperature 23.3}    ;; °C


(i2c/close bus)
```

## License

Copyright © 2017-2018 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
