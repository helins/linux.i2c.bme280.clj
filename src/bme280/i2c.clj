(ns bme280.i2c

  "I2C interface for BME280 environmental sensor"

  {:author "Adam Helinski"}

  (:require [icare.core :as i2c]))




;;;;;;;;;;


(defn- -oversampling

  "Get the hex oversampling value from a kw"

  [^long oversampling]

  (case oversampling
    0  0x00
    1  0x01
    2  0x02
    4  0x03
    8  0x04
    16 0x05))




(defn- -to-unsigned-byte

  "Read a byte from a byte array as an unsigned byte"

  [^bytes ba i]

  (bit-and (get ba
                i)
           0xff))



(defn- -to-signed-short

  "Read 2 bytes from a byte array and combine them at bit `n` to form a signed short"

  [n ^bytes ba i-lsb i-msb]

  (bit-or (bit-and (aget ba
                         i-lsb)
                   0xff)
          (bit-shift-left (aget ba
                                i-msb)
                          n)))




(defn- -to-signed-short-4

  "Read 4 bits and a byte from a byte array as a signed short"

  [^bytes ba i-lsb i-msb]

  (-to-signed-short 4
                    ba
                    i-lsb
                    i-msb))




(defn- -to-signed-short-8

  "Read 2 bytes from a byte array as a signed short"

  [^bytes ba i-lsb i-msb]

  (-to-signed-short 8
                    ba
                    i-lsb
                    i-msb))




(defn- -to-unsigned-short

  "Read 2 bytes from a byte array as an unsigned short"

  [^bytes ba i-lsb i-msb]

  (bit-or (bit-and (aget ba
                         i-lsb)
                   0xff)
          (bit-shift-left (bit-and (aget ba
                                         i-msb)
                                   0xff)
                           8)))




(defn- -to-unsigned-int

  "Reads 4 bits and 2 bytes from a byte array as an unsigned int"

  [^bytes ba i-xlsb i-lsb i-msb]

  (bit-and (bit-or (bit-and (aget ba
                                  i-xlsb)
                            0x0f)
                   (bit-shift-left (bit-and (aget ba
                                                  i-lsb)
                                            0xff)
                                   4)
                   (bit-shift-left (bit-and (aget ba
                                                  i-msb)
                                            0xff)
                                   12))
           0xffffffff))




;;;;;;;;;;


(defn id

  "Get the chip identification number.

   Throws an IOException if an error occurs during reading."

  [bus]

  (i2c/read-byte bus
                 0xd0))




(defn status

  "Get the status of the device.

   Returns a map containing :

     :running-conversion?  true  when a conversion is running
                           false when results have been transfered to the data registers

     :copying-NVM?         true  when NVM data are being copied to image registers
                           false when the copying is done
  
   Throws an IOException if an error occurs during reading."

  [bus]

  (let [[b1
         b2] (i2c/read-byte bus
                            0xf3)]
    {:running-conversion? (bit-test b1
                                    3)
     :copying-NVM?        (bit-test b2
                                    0)}))





(defn soft-reset

  "Do a soft reset.

   Returns the given I2C bus.
  
   Throws an IOException if an error occurs during writing."

  [bus]

  (i2c/write-byte bus
                  0xe0
                  0xb6))




(defn control-humidity

  "Control oversampling of humidity data.

   <!> Changes to this register only become effective after `control-measurements`.

       Ideally humidity would be part of `control-measurements` but as it is another
       register, hence a separate write, we keep it explicitly separate.

   Returns the given I2C bus.

   Throws an IOException if an error occurs during writing.
  
   Cf. `control-measurements`
       `configure`"

  [bus oversampling]

  (i2c/write-byte bus
                  0xf2
                  (-oversampling oversampling))
  bus)




(defn control-measurements

  "Choose a mode and control oversampling of temperature and pressure data.
  
   Mode is one of :
  
       :sleep
           No operation, all registers accessible, lower power, selected after startup.

       :forced
           Perform one measurement, store results and return to sleep mode. To do another
           measurement, call this function with forced mode again.
           Recommended for low sampling rate, or \"à la carte\".

       :normal
           Perpetual cycling of measurements and inactive periods.
           Recommended for high sampling rate.

   If the device is currently performing a measurement, execution of mode switching is delayed
   until the end of the currently running measurement period. Further mode change commands, such
   as `control-humidity`, are ignored until the mode change command has been executed.

   Oversamplings are one of (where 0 = disable measurement) :

       #{0 1 2 4 8 16}

   Returns the given I2C bus.
  
   Throws an IOException if an error occurs during writing.
  
   Cf. `control-humidity`
       `configure`"

  [bus mode oversampling-temperature oversampling-pressure]

  (i2c/write-byte bus
                  0xf4
                  (bit-or (case mode
                            :sleep  0x00
                            :forced 0x01
                            :normal 0x03)
                          (bit-shift-left (-oversampling oversampling-pressure)
                                          2)
                          (bit-shift-left (-oversampling oversampling-temperature)
                                          5)))
  bus)




(defn configure

  "Configure the inactive duration and the IIR filter.

   The inactive duration is relevant to the normal mode and basically sets the period in milliseconds.
   Must be one of :

       #{0.5 10 20 62.5 125 250 500 1000}


   The internal IIR filter can be configured for when the environmental pressure is subject to many short-term
   changes such as slamming a door or the wind blowing on the sensor. This will also affect temperature measurements.
  
   The value is one of these coefficients :

       coefficient | samples to reach >= 75% of step response
       ------------------------------------------------------
          0 (off)  |  1
          2        |  2
          4        |  5
          8        | 11
         16        | 22

   <!> Changes to this register only become effective after `control-measurements`.

   Returns the given I2C bus.

   Throws an IOException if an error occurs during writing.
  
   Cf. `control-humidity`
       `control-measurements`"
  
  [bus inactive-duration ^long iir-filter]

  ;; the first bit is for enabling 3-wire SPI instead of 4-wire
  ;; since we use I2C it is set to 0 and we do not care
  (i2c/write-byte bus
                  0xf5
                  (bit-or (bit-shift-left (case iir-filter
                                             0 0x00
                                             2 0x01
                                             4 0x02
                                             8 0x03
                                            16 0x04)
                                          1)
                          (bit-shift-left (case inactive-duration
                                               0.5 0x00
                                              10   0x06
                                              20   0x07
                                              62.5 0x01
                                             125   0x02
                                             250   0x03
                                             500   0x04
                                            1000   0x05)
                                          4)))
  bus)




(defn compensation-words

  "Get compensation words for all sensors.
  
   Each sensing element behaves differently. Hence, raw data from the sensors must be
   calibrated using those compensation words.

   Returns a byte array containing 32 bytes that need to be combined into multi-byte coefficients.

   Throws an IOException when an error occurs during one of the reads.
  
   Cf. `coefficients`"

  [bus]

  (let [ba (byte-array 32)]
    ;; temperature + pressure
    (i2c/read-bytes bus
                    0x88
                    ba
                    0
                    24)
    ;; humidity
    (i2c/read-bytes bus
                    0xa1
                    ba
                    24
                    1)
    (i2c/read-bytes bus
                    0xe1
                    ba
                    25
                    7)
    ba))




(defn coefficients

  "Extract coefficients from compensation words.
  
   Cf. `compensation-words`"

  [^bytes cw]

  {:T1 (-to-unsigned-short cw 0 1)
   :T2 (-to-signed-short-8 cw 2 3)
   :T3 (-to-signed-short-8 cw 4 5)
   :P1 (-to-unsigned-short cw 6 7)
   :P2 (-to-signed-short-8 cw 8 9)
   :P3 (-to-signed-short-8 cw 10 11)
   :P4 (-to-signed-short-8 cw 12 13)
   :P5 (-to-signed-short-8 cw 14 15)
   :P6 (-to-signed-short-8 cw 16 17)
   :P7 (-to-signed-short-8 cw 18 19)
   :P8 (-to-signed-short-8 cw 20 21)
   :P9 (-to-signed-short-8 cw 22 23)
   :H1 (-to-unsigned-byte  cw 24)
   :H2 (-to-signed-short-8 cw 25 26)
   :H3 (-to-unsigned-byte  cw 27)
   :H4 (-to-signed-short-4 cw 29 28)
   :H5 (bit-or (bit-shift-right (bit-and (aget cw
                                               29)
                                         0xff)
                                4)
               (bit-shift-left (aget cw
                                     30)
                               4))
   :H6 (aget cw 31)})





(defn raw-data

  "Read 8 bytes representing pressure, temperature and humidity raw data.

   Those values have to be adjusted using coefficients.

   It is best to read everything even if a particular sensor is not needed, hence this is
   what it does.

   Returns the given or newly created byte array.
  
   Throws an IOException when an error occurs during reading.
  
   Cf. `coefficients`
       `precise-temperature`
       `pressure`
       `humidity`"

  ([bus]

   (let [ba (byte-array 8)]
     (i2c/read-bytes bus
                     0xf7
                     ba)
     ba))


  ([bus ba offset]

   (i2c/read-bytes bus
                   0xf7
                   ba
                   offset
                   8)
   ba)

  ([bus ba]

   (raw-data bus
             ba
             0)))




;;;;;;;;;;


(defn precise-temperature

  "Given compensation words and raw data, compute the \"precise\" temperature.

   This value can then be converted to °C or used for computing pressure and humidity.

   Cf. `coefficients`
       `raw-data`
       `to-celcius`
       `pressure`
       `humidity`
  
       Appendix 8.1 in datasheet"

  [{:as   coeffs
    :keys [T1
           T2
           T3]}
   ^bytes data]
   
  (let [t    (-to-unsigned-int   data 5 4 3)
        ;;   computation
        var1 (* (- (/ t
                      16384)
                   (/ T1
                      1024))
                T2)
        var2 (* (Math/pow (- (/ t
                                131072)
                             (/ T1
                                8192))
                          2)
                T3)]
    (+ var1
       var2)))




(defn to-celcius

  "Convert a precise temperature to °C.
  
   Cf. `precise-temperature`"

  [t]

  (/ t
     5120))




(defn pressure

  "Given compensation words and raw data, compute the pressure in Pa.

   Cf. `coefficients`
       `raw-data`
       `precise-temperature`
  
       Appendix 8.1 in datasheet"

  [{:as   coeffs
    :keys [P1
           P2
           P3
           P4
           P5
           P6
           P7
           P8
           P9]}
   ^bytes data
   precise-temp]

  (let [p    (-to-unsigned-int   data 2 1 0)
        ;;   computation
        var1 (- (/ precise-temp
                   2)
                64000)
        var2 (/ (* var1
                   var1
                   P6)
                32768)
        var2 (+ var2
                (* var1
                   P5
                   2))
        var2 (+ (/ var2
                   4)
                (* P4
                   65536))
        var1 (/ (+ (/ (* P3
                         var1
                         var1)
                      524288)
                   (* P2
                      var1))
                524288)
        var1 (* (inc (/ var1
                        32768))
                P1)]
    (if (zero? var1)
      0
      (let [p'   (- 1048576
                    p)
            p'   (/ (* (- p'
                          (/ var2
                             4096))
                       6250)
                    var1)
            var1 (/ (* P9
                       p'
                       p')
                    2147483648)
            var2 (/ (* p'
                       P8)
                    32768)]
        (+ p'
           (/ (+ var1
                 var2
                 P7)
              16))))))




(defn humidity

  "Given compensation words and raw data, compute the humidity in %rH.
  
   Cf. `coefficients`
       `raw-data`
       `precise-temperature`
    
       Appendix 8.1 in datasheet"

  [{:as   coeffs
    :keys [H1
           H2
           H3
           H4
           H5
           H6]}
   ^bytes data
   precise-temp]

  (let [h     (-to-unsigned-short data 7 6)
        ;;    computation
        varh  (- precise-temp
                 76800)
        varh  (* (- h
                    (+ (* H4
                          64)
                       (* (/ H5
                             16384)
                          varh)))
                 (* (/ H2
                       65536)
                    (inc (* (/ H6
                               67108864)
                            varh
                            (inc (* (/ H3
                                       67108864)
                                    varh))))))
        varh (* varh
                (- 1
                   (/ (* H1
                         varh)
                      524288)))]
    (cond
      (> varh
         100)  100
      (< varh
         0)    0
      :else    varh)))




(defn sensors
  
  "Given compensation words and raw data, compute the temperature, the pressure and
   the humidity.
  
   Cf. `coefficients`
       `precise-temperature`
       `to-celcius`
       `pressure`
       `humidity`"

  [coeffs ^bytes data]

  (let [t (precise-temperature coeffs
                               data)]
    {:temperature (to-celcius t)
     :pressure    (pressure coeffs
                            data
                            t)
     :humidity    (humidity coeffs
                            data
                            t)}))




;;;;;;;;;;


(defn measure-typ

  "Compute the typical measurement time in milliseconds given sensor oversamplings"

  [o-t o-p o-h]

  (+ 1
     (* 2
        o-t)
     (if (pos? o-p)
       (+ (* 2
             o-p)
          0.5)
       0)
     (if (pos? o-h)
       (+ (* 2
             o-h)
          0.5)
       0)))




(defn measure-max

  "Compute the maximum measurement time in milliseconds given sensor oversamplings"

  [o-t o-p o-h]

  (+ 1.25
     (* 2.3
        o-t)
     (if (pos? o-p)
       (+ (* 2.3
             o-p)
          0.575)
       0)
     (if (pos? o-h)
       (+ (* 2.3
             o-h)
          0.575)
       0)))



(defn rate

  "Compute the output data rate in hertz given measurement time.

   In forced mode (ie. on user demand), it means the achievable rate.

   In normal mode, the standby time must be provided.

   Cf. `measure-typ`
       `control-measurements`"

  ([measure-ms]

   (/ 1000
      measure-ms))


  ([standby-ms measure-ms]

   (/ 1000
      (+ measure-ms
         standby-ms))))




(defn iir-response-time

  "Compute the response time of the sensors given the measurement rate and taking
   into account the selected coefficient for the IIR filter.

   Cf. `rate`
       `config`"

  [rate ^long coefficient]

  (* (/ 1000
        rate)
     (case coefficient
        0  1
        2  2
        4  5
        8 11
       16 22)))
