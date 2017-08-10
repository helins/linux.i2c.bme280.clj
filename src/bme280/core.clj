(ns bme280.core

  (:require [pi4clj.i2c :as i2c])
  (:gen-class))




;;;;;;;;;;


(defn coeff

  "Compute the coefficient"

  [coeffs a b]

  (+ (* 256
        (nth coeffs
             b))
     (nth coeffs
          a)))



(defn coeff-corr

  "Compute the corrected coefficient"

  [coeffs a b]

  (let [n (coeff coeffs
                 a
                 b)]
    (if (> n
           32767)
      (- n
         65536)
      n)))




(defn adc

  "Convert data to 19bits"

  [data a b c]

  (/ (+ (* 65536
           (nth data
                a))
        (* 256
           (nth data
                b))
        (bit-and (nth data
                      c)
                 0xf0))
     16))




(defn temperature

  "Given an I2C bus, get the temperature"

  [bus]

  (i2c/select-slave bus
                    0x76)
  (let [coeffs (i2c/rd-reg bus
                           0x88
                           24)
        coeff-1 (coeff coeffs
                       0
                       1)
        coeff-2 (coeff-corr coeffs
                            2
                            3)
        coeff-3 (coeff-corr coeffs
                            4
                            5)
        data    (i2c/rd-reg bus
                            0xf7
                            8)
        adc'    (adc data
                     3
                     4
                     5)
        off-1   (- (/ adc'
                      16384)
                   (* (/ coeff-1
                         1024)
                      coeff-2))
        off-2   (- (/ adc'
                      131072)
                   (* (/ coeff-1
                         8192)
                      (- (/ adc'
                            131072)
                         (/ coeff-1
                            8192))
                      coeff-3))]
    (println :coefficients coeff-1 coeff-2 coeff-3)
    (println :data (nth data 3) (nth data 4) (nth data 5))
    (println :or (bit-and (nth data
                               5)
                          0xf0))
    (println :adc adc')
    (println :offsets (double off-1) (double off-2))
    ;; bad offsets
    (double (/ (+ off-1
               off-2)
            5120))))



;;;;;;;;;;


(defn -main
  
  [& args]
  
  (println "Starting rpi..."))
