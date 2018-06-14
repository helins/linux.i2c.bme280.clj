(ns dvlopt.linux.i2c.bme280

  "Interface for talking to BME280 sensors via I2C.

   Any IO operation might throw in case of failure, and do not forget to always select the proper slave.

   Relies on :
  
     https://github.com/dvlopt/linux.i2c.clj"

  {:author "Adam Helinski"}
  
  (:require [dvlopt.linux.i2c :as i2c]
            [dvlopt.void      :as void]))




;;;;;;;;;; Misc


(def defaults

  "Defaults values for options and keys used throughout this namespace."

  {::iir-filter               0
   ::mode                     :normal
   ::oversampling.humidity    :x1
   ::oversampling.pressure    :x1
   ::oversampling.temperature :x1
   ::standby                  :0.5-ms})




(defn- -restrict-number

  ;; Restricts number `n` to be between `lower` and `upper`, inclusive.

  [lower upper n]

  (cond
    (< n
       lower) lower
    (> n
       upper) upper
    :else     n))




;;;;;;;;;; Bit manipulations


(defn- -short

  ;; Reads 2 bytes from a vector and combines them at bit `n` to form a signed short.

  [bs i-lsb i-msb n]

  (unchecked-short (bit-or (nth bs
                                i-lsb)
                           (bit-shift-left (nth bs
                                                i-msb)
                                           n))))




(defn- -short--4

  ;; Reads 4 bits and a byte from a vector as a signed short."

  [bs i-lsb i-msb]

  (-short bs
          i-lsb
          i-msb
          4))




(defn- -short--8

  ; Reads 2 bytes from a vector as a signed short."

  [bs i-lsb i-msb]

  (-short bs
          i-lsb
          i-msb
          8))




(defn- -uint

  ;; Reads 4 bits and 2 bytes from a byte array as an unsigned int.

  [bs i-xlsb i-lsb i-msb]

  (bit-and (bit-or (bit-and (nth bs
                                 i-xlsb)
                            0x0f)
                   (bit-shift-left (nth bs
                                        i-lsb)
                                   4)
                   (bit-shift-left (nth bs
                                        i-msb)
                                   12))
           0xffffffff))




(defn- -ushort

  ;; Reads 2 bytes from a byte array as an unsigned short.

  [bs i-lsb i-msb]

  (bit-or (nth bs
               i-lsb)
          (bit-shift-left (nth bs
                               i-msb)
                           8)))




;;;;;;;;;; Misc conversions


(defn- -iir-filter->byte

  ;; Converts an iir filter value to a byte.

  [^long iir-filter]

  (case iir-filter
     0 0x00
     2 0x01
     4 0x02
     8 0x03
    16 0x04))




(defn- -mode->byte

  ;; Converts a mode into a byte.

  [mode]

  (condp identical?
         mode
    :forced 0x01
    :normal 0x03
    :sleep  0x00))




(defn- -oversampling->byte

  ;;"Converts an oversampling into a byte.

  [oversampling]

  (condp identical?
         oversampling
    :x0  0x00
    :x1  0x01
    :x2  0x02
    :x4  0x03
    :x8  0x04
    :x16 0x05))




(defn- -oversampling->multiplier

  ;; Converts an oversampling value into a multiplier.

  [oversampling]

  (condp identical?
         oversampling
    :x0  0
    :x1  1
    :x2  2
    :x4  4
    :x8  8
    :x16 16))




(defn- -standby->byte

  ;; Converts a standby time into a byte.

  [standby]

  (condp identical?
         standby
    :0.5-ms  0x00
    :10-ms   0x06
    :20-ms   0x07
    :62.5-ms 0x01
    :125-ms  0x02
    :250-ms  0x03
    :500-ms  0x04
    :1000-ms 0x05))




(defn- -standby->ms

  ;; Converts a standby time into milliseconds.

  [standby]

  (condp identical?
         standby
    :0.5-ms  0.5 
    :10-ms   10
    :20-ms   20
    :62.5-ms 62.5
    :125-ms  125
    :250-ms  250
    :500-ms  500
    :1000-ms 1000))




;;;;;;;;;; Computing duration of a measurement cycle


(defn- -cycle-duration--maximum

  ;; Given oversamplings multipliers, computes the maximal duration of a measurement cycle.

  [oversampling--humidity oversampling--pressure oversampling--temperature]

  (+ 1.25
     (if (pos? oversampling--humidity)
       (+ (* 2.3
             oversampling--humidity)
          0.575)
       0)
     (if (pos? oversampling--pressure)
       (+ (* 2.3
             oversampling--pressure)
          0.575)
       0)
     (* 2.3
        oversampling--temperature)))




(defn- -cycle-duration--typical

  ;; Given oversamplings multipliers, computes the typical duration of a measurement cycle.

  [oversampling--humidity oversampling--pressure oversampling--temperature]

  (+ 1
     (if (pos? oversampling--humidity)
       (+ (* 2
             oversampling--humidity)
          0.5)
       0)
     (if (pos? oversampling--pressure)
       (+ (* 2
             oversampling--pressure)
          0.5)
       0)
     (* 2
        oversampling--temperature)))




(defn cycle-duration

  "Given a configuration map, computes the duration of a single measurement cycle in milliseconds.
  
   Returns a map containing :maximum, the maximum duration
                            :typical, the typical duration.
  
   Cf. `configure`"

  ([]

   (cycle-duration nil))


  ([configuration]

   (let [iir-filter                (case ^long (void/obtain ::iir-filter
                                                            configuration
                                                            defaults)
                                      0  1
                                      2  2
                                      4  5
                                      8 11
                                     16 22)
         oversampling--humidity    (-oversampling->multiplier (void/obtain ::oversampling.humidity
                                                                           configuration
                                                                           defaults))
         oversampling--pressure    (-oversampling->multiplier (void/obtain ::oversampling.pressure
                                                                           configuration
                                                                           defaults))
         oversampling--temperature (-oversampling->multiplier (void/obtain ::oversampling.temperature
                                                                          configuration
                                                                           defaults))
         standby                   (condp identical?
                                          (void/obtain ::mode
                                                       configuration
                                                       defaults)
                                     :forced 0
                                     :normal (-standby->ms (void/obtain ::standby
                                                                        configuration
                                                                        defaults)))]
     (reduce (fn add-duration [hmap [k f]]
               (assoc hmap
                      k
                      (long (Math/ceil (+ (* (f oversampling--humidity
                                                oversampling--pressure
                                                oversampling--temperature)
                                             iir-filter)
                                          standby)))))
             {}
             [[:maximum -cycle-duration--maximum]
              [:typical -cycle-duration--typical]]))))




;;;;;;;;;; Configuration


(defn- -configure

  ;; Configures iir filter and standby time.
  
  [bus configuration]

  (let [;; The first bit is for enabling 3-wire SPI instead of 4-wire but since we use
        ;; I2C, we do not care.

        b (bit-or (bit-shift-left (-iir-filter->byte (void/obtain ::iir-filter
                                                                  configuration
                                                                  defaults))
                                  1)
                  (bit-shift-left (-standby->byte (void/obtain ::standby
                                                               configuration
                                                               defaults))
                                  4))]
    (i2c/write bus
               [0xf5 b]))
  nil)




(defn- -control-humidity

  ;; Configures humidity

  [bus configuration]

  (i2c/write bus
             [0xf2 (-oversampling->byte (void/obtain ::oversampling.humidity
                                                     configuration
                                                     defaults))])
  nil)




(defn- -control-measurements

  ;; Configures mode, pressure oversampling, and temperature oversampling.

  [bus configuration]

  (let [b (bit-or (-mode->byte (void/obtain ::mode
                                            configuration
                                            defaults))
                  (bit-shift-left (-oversampling->byte (void/obtain ::oversampling.pressure
                                                                    configuration
                                                                    defaults))
                                  2)
                  (bit-shift-left (-oversampling->byte (void/obtain ::oversampling.temperature
                                                                    configuration
                                                                    defaults))
                                  5))]
    (i2c/write bus
               [0xf4 b]))
  nil)




(defn configure

  "Configures a previously selected slave device.

   The configuration map may have the following options :

     ::iir-filter

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

     ::mode

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
       until the end of the currently running measurement period.

     ::oversampling.humidity
     ::oversampling.pressure
     ::oversampling.temperature

       Oversamplings are one of (where 0 = disable measurement) :

           #{:x0 :x1 :x2 :x4 :x8 :x16}

     ::standby

       Inactive duration period between 2 measurements, relevant for the :normal mode, in milliseconds.
       Must be one of :

           #{:0.5-ms :10-ms :20-ms :62.5-ms :125-ms :250-ms :500-ms :1000-ms}


   Cf. `defaults` for used values for missing options"

  ([bus]

   (configure bus
              nil))


  ([bus configuration]

   (doseq [f [-configure
              -control-humidity
              -control-measurements]]
     (f bus
        configuration))
   nil))




;;;;;;;;;; Sensor data


(defn compensation-words

  "Retrieves compensation words from the slave.

   Each sensor behaves a little differently, hence raw data must later be adjusted using those words."

  [bus]

  (let [bs (reduce (fn read-bytes [vect [register n]]
                     (i2c/write bus
                                [register])
                     (into vect
                           (i2c/read bus
                                     n)))
                   []
                   [[0x88 24]
                    [0xa1  1]
                    [0xe1  7]])]
    {::H1 (nth bs
               24)
     ::H2 (-short--8 bs
                     25
                     26)
     ::H3 (nth bs
               27)
     ::H4 (-short--4 bs
                     29
                     28)
     ::H5 (bit-or (bit-shift-right (nth bs
                                        29)
                                   4)
                  (bit-shift-left (nth bs
                                       30)
                                  4))
     ::H6 (unchecked-byte (nth bs
                               31))
     ::P1 (-ushort bs
                   6
                   7)
     ::P2 (-short--8 bs
                     8
                     9)
     ::P3 (-short--8 bs
                     10
                     11)
     ::P4 (-short--8 bs
                     12
                     13)
     ::P5 (-short--8 bs
                     14
                     15)
     ::P6 (-short--8 bs
                     16
                     17)
     ::P7 (-short--8 bs
                     18
                     19)
     ::P8 (-short--8 bs
                     20
                     21)
     ::P9 (-short--8 bs
                     22
                     23)
     ::T1 (-ushort bs
                   0
                   1)
     ::T2 (-short--8 bs
                     2
                     3)
     ::T3 (-short--8 bs
                     4
                     5)}))




(defn- -humidity

  ;; Computes and adjusts the humidity.

  ^double

  [raw-data compensation-words precise-temperature]

  (let [h     (-ushort raw-data
                       7
                       6)
        H1    (::H1 compensation-words)
        H2    (::H2 compensation-words)
        H3    (::H3 compensation-words)
        H4    (::H4 compensation-words)
        H5    (::H5 compensation-words)
        H6    (::H6 compensation-words)
        ;;    computation
        varh  (- precise-temperature
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
    (-restrict-number 0
                      100
                      varh)))




(defn- -pressure

  ;; Computes and adjusts the pressure.

  ^double

  [raw-data compensation-words precise-temperature]

  (let [p    (-uint raw-data
                    2
                    1
                    0)
        P1   (::P1 compensation-words)
        P2   (::P2 compensation-words)
        P3   (::P3 compensation-words)
        P4   (::P4 compensation-words)
        P5   (::P5 compensation-words)
        P6   (::P6 compensation-words)
        P7   (::P7 compensation-words)
        P8   (::P8 compensation-words)
        P9   (::P9 compensation-words)
        ;;   computation
        var1 (- (/ precise-temperature
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
        (-restrict-number 300
                          1100
                          (+ p'
                             (/ (+ var1
                                   var2
                                   P7)
                                16)))))))




(defn- -precise-temperature

  ;; Computes and adjusts the \"precise\" temperature which is then used for computing the humidity,
  ;; pressure, and temperature in celcius."

  ^double

  [raw-data compensation-words]
   
  (let [t    (-uint raw-data
                    5
                    4
                    3)
        T1   (::T1 compensation-words)
        T2   (::T2 compensation-words)
        T3   (::T3 compensation-words)
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
    (-restrict-number -250000
                      250000
                      (+ var1
                         var2))))




(defn- -temperature

  ;; Converts a \"precise\" temperature to celsius.

  ^double

  [precise-temperature]

  (-restrict-number -40
                    85
                    (/ precise-temperature
                       5120)))




(defn read-sensors

  "Reads data from the sensors and adjusts it using the given compensation-words.

   Returns a map containing :

     ::humidity    in %rH
     ::pressure    in Pa
     ::temperature in °C

  
   Cf. `compensation-words`"

  [bus compensation-words]

  (let [raw-data            (do 
                              (i2c/write bus
                                         [0xf7])
                              (i2c/read bus
                                        8))
        precise-temperature (-precise-temperature raw-data
                                                  compensation-words)]
    {::humidity    (-humidity raw-data
                              compensation-words
                              precise-temperature)
     ::pressure    (-pressure raw-data
                              compensation-words
                              precise-temperature)
     ::temperature (-temperature precise-temperature)}))




;;;;;;;;;; Misc operations


(defn soft-reset

  "Orders the slave to perform a soft reset."

  [bus]

  (i2c/write bus
             [0xe0 0xb6])
  nil)




(defn status

  "Retrieves the current status of the slave and returns a map containing

     ::copying-nvm?         True when NVM data is being copied to image registers.
     ::running-conversion?  True when a conversion is currently running."

  [bus]

  (i2c/write bus
             [0xf3])
  (let [b (first (i2c/read bus
                           1))]
    {::copying-NVM?        (bit-test b
                                     0)
     ::running-conversion? (bit-test b
                                     3)}))
