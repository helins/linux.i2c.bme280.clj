(ns dvlopt.i2c.bme280

  "This namespace provides specs, functions and values for talking the BME280 sensors.

   The API aims to ressemble the datasheet.

   See `io` for a description of possibles commands. Functions from this API allow the user
   to prepare data for writing, understand data after reading and perform other tasks such
   as computing the duration of a single measure.

   The IO itself is left to the discretion of the user. We recommend using 'dvlopt.i2c',
   a clojure library for I2C.

  
   Cf. https://github.com/dvlopt/i2c"

  {:author "Adam Helinski"}
  
  (:require [clojure.spec.alpha :as s]))




;;;;;;;;;; Specs - Primitives


(s/def ::byte

  (s/int-in Byte/MIN_VALUE
            (inc Byte/MAX_VALUE)))


(s/def ::short

  (s/int-in Short/MIN_VALUE
            (inc Short/MAX_VALUE)))


(s/def ::ubyte

  (s/int-in 0
            256))


(s/def ::ushort

  (s/int-in 0
            (Math/pow 2
                      16)))




;;;;;;;;;; Specs - API


(s/def ::buffer.cw

  (s/and bytes?
         #(>= (count %)
              32)))


(s/def ::buffer.data

  (s/and bytes?
         #(>= (count %)
              24)))


(s/def ::copying-NVM?

  boolean?)


(s/def ::data

  (s/keys :req [::humidity
                ::pressure
                ::temperature]))


(s/def ::duration.max

  (s/double-in :max 112.8
               :min 0))


(s/def ::duration.typical

  (s/double-in :max 98
               :min 0))

(s/def ::duration.with-iir-filter

  (s/double-in :max 2481.6
               :min 0))


(s/def ::humidity

  (s/double-in :max 100
               :min 0))


(s/def ::iir-filter

  #{0
    2
    4
    8
    16})


(s/def ::length

  (s/and int?
         #(>= %
              0)))

(s/def ::mode

  #{:forced
    :normal
    :sleep})


(s/def ::operation

  #{:read
    :write})


(s/def ::oversampling

  #{:x0
    :x1
    :x2
    :x4
    :x8
    :x16})

      
(s/def ::precise-temperature

  (s/double-in :max 250000
               :min -250000))


(s/def ::pressure

  (s/double-in :max 1100
               :min 0))


(s/def ::register

  ::ubyte)


(s/def ::registers

  (s/coll-of (s/keys :req [::length
                           ::register])))


(s/def ::running-conversion?

  boolean?)


(s/def ::see

  #{`compensation-words
    `configure
    `control-humidity
    `control-measurements
    `data
    `status})


(s/def ::standby

  #{:0.5-ms
    :10-ms
    :20-ms
    :62.5-ms
    :125-ms
    :250-ms
    :500-ms
    :1000-ms})


(s/def ::status

  (s/keys :req [::copying-NVM?
                ::running-conversion?]))


(s/def ::temperature

  (s/double-in :max 85
               :min -40))


(s/def ::write

  ::ubyte)




;;;;;;;;;; Specs - API - Compensations words


(s/def ::compensation-words

  (s/keys :req [::H1
                ::H2
                ::H3
                ::H4
                ::H5
                ::H6
                ::P1
                ::P2
                ::P3
                ::P4
                ::P5
                ::P6
                ::P7
                ::P8
                ::P9
                ::T1
                ::T2
                ::T3]))


(s/def ::H1

  ::ubyte)


(s/def ::H2

  ::short)


(s/def ::H3

  ::ubyte)


(s/def ::H4

  ::short)


(s/def ::H5

  ::short)


(s/def ::H6

  ::byte)


(s/def ::P1

  ::ushort)


(s/def ::P2

  ::short)


(s/def ::P3

  ::short)


(s/def ::P4

  ::short)


(s/def ::P5

  ::short)


(s/def ::P6

  ::short)


(s/def ::P7

  ::short)


(s/def ::P8

  ::short)


(s/def ::P9

  ::short)


(s/def ::T1

  ::ushort)


(s/def ::T2

  ::short)


(s/def ::T3

  ::short)




;;;;;;;;;; Private - Bit manipulations


(defn- -short

  "Reads 2 bytes from a byte array and combines them at bit `n` to form a signed short."

  [n ^bytes ba i-lsb i-msb]

  (bit-or (bit-and (aget ba
                         i-lsb)
                   0xff)
          (bit-shift-left (aget ba
                                i-msb)
                          n)))




(defn- -short--4

  "Reads 4 bits and a byte from a byte array as a signed short."

  [^bytes ba i-lsb i-msb]

  (-short 4
          ba
          i-lsb
          i-msb))




(defn- -short--8

  "Reads 2 bytes from a byte array as a signed short."

  [^bytes ba i-lsb i-msb]

  (-short 8
          ba
          i-lsb
          i-msb))




(defn- -ubyte

  "Reads a byte from a byte array as an unsigned byte."

  [^bytes ba i]

  (bit-and (get ba
                i)
           0xff))




(defn- -uint

  "Reads 4 bits and 2 bytes from a byte array as an unsigned int."

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




(defn- -ushort

  "Reads 2 bytes from a byte array as an unsigned short."

  [^bytes ba i-lsb i-msb]

  (bit-or (bit-and (aget ba
                         i-lsb)
                   0xff)
          (bit-shift-left (bit-and (aget ba
                                         i-msb)
                                   0xff)
                           8)))




;;;;;;;;;; Private - Conversions


(s/fdef -iir-filter

  :args (s/cat :iir-filter ::iir-filter)
  :ret  ::ubyte)


(defn- -iir-filter

  "Converts an iir filter value to a byte."

  [^long iir-filter]

  (case iir-filter
    0  0x00
    2  0x01
    4  0x02
    8  0x03
    16 0x04))




(s/fdef -mode

  :args (s/cat :mode ::mode)
  :ret  ::ubyte)


(defn- -mode

  "Converts a mode into a byte."

  [mode]

  (condp identical?
         mode
    :forced 0x01
    :normal 0x03
    :sleep  0x00))




(s/fdef -oversampling

  :args (s/cat :oversampling ::oversampling)
  :ret  ::ubyte)


(defn- -oversampling

  "Converts an oversampling into a byte."

  [oversampling]

  (condp identical?
         oversampling
    :x0  0x00
    :x1  0x01
    :x2  0x02
    :x4  0x03
    :x8  0x04
    :x16 0x05))




(s/fdef -oversampling--mult

  :args (s/cat :oversampling ::oversampling)
  :ret  #{0 1 2 4 8 16})


(defn- -oversampling--mult

  "Converts an oversampling value into a multiplyer."

  [oversampling]

  (condp identical?
         oversampling
    :x0  0
    :x1  1
    :x2  2
    :x4  4
    :x8  8
    :x16 16))




(s/fdef -standby

  :args (s/cat :standby ::standby)
  :ret  ::ubyte)


(defn- -standby

  "Converts a standby time into a byte."

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




;;;;;;;;;; Private - Misc


(s/fdef -restrict-number

  :args (s/and (s/cat :lower number?
                      :upper number?
                      :n     number?)
               #(>= (:upper %)
                    (:lower %)))
  :fn   #(let [args   (:args %)
               result (:ret %)]
           (or (Double/isNaN result)
               (and (>= result
                        (:lower args))
                    (<= result
                        (:upper args)))))
  :ret  number?)


(defn- -restrict-number

  "Restricts number `n` to be between `lower` and `upper`, inclusive."

  [lower upper n]

  (cond
    (< n
       lower) lower
    (> n
       upper) upper
    :else     n))




;;;;;;;;;; API - IO


(def io

  "Map describing IO commands."

  {:compensation-words   {::length    32
                          ::operation :read
                          ::registers [{::length   24
                                        ::register 0x88}
                                       {::length   1
                                        ::register 0xa1}
                                       {::length   7
                                        ::register 0xe1}]
                          ::see       `compensation-words}
   :configure            {::length    1
                          ::operation :write
                          ::register  0xf5
                          ::see       `configure}
   :control-humidity     {::length    1
                          ::operation :write
                          ::register  0xf2
                          ::see       `control-humidity}
   :control-measurements {::length    1
                          ::operation :write
                          ::register  0xf4
                          ::see       `control-measurements}
   :data                 {::length    8
                          ::operation :read
                          ::register  0xf7
                          ::see       `data}
   :id                   {::length    1
                          ::operation :read
                          ::register  0xd0}
   :soft-reset           {::length    1
                          ::operation :write
                          ::register  0xe0
                          ::write     0xb6 }
   :status               {::length    1
                          ::operation :read
                          ::register  0xf3
                          ::see       `status}})




(s/fdef compensation-words

  :args (s/cat :buffer-cw ::buffer.cw)
  :ret  ::compensation-words)


(defn compensation-words

  "Prepares compensations words used for adjusting sensor data.
  
   Cf. (:compensation-words io)"

  [^bytes buffer-cw]

  {::H1 (-ubyte  buffer-cw 24)
   ::H2 (-short--8 buffer-cw 25 26)
   ::H3 (-ubyte  buffer-cw 27)
   ::H4 (-short--4 buffer-cw 29 28)
   ::H5 (bit-or (bit-shift-right (bit-and (aget buffer-cw
                                                29)
                                          0xff)
                                 4)
                (bit-shift-left (aget buffer-cw
                                      30)
                                4))
   ::H6 (aget buffer-cw
              31)
   ::P1 (-ushort buffer-cw 6 7)
   ::P2 (-short--8 buffer-cw 8 9)
   ::P3 (-short--8 buffer-cw 10 11)
   ::P4 (-short--8 buffer-cw 12 13)
   ::P5 (-short--8 buffer-cw 14 15)
   ::P6 (-short--8 buffer-cw 16 17)
   ::P7 (-short--8 buffer-cw 18 19)
   ::P8 (-short--8 buffer-cw 20 21)
   ::P9 (-short--8 buffer-cw 22 23)
   ::T1 (-ushort buffer-cw 0 1)
   ::T2 (-short--8 buffer-cw 2 3)
   ::T3 (-short--8 buffer-cw 4 5)})




(s/fdef configure

  :args (s/cat :iir-filter ::iir-filter
               :standby    ::standby)
  :ret  ::ubyte)


(defn configure

  "Prepares a byte for configuring the iir filter and the standby time.
  
   Cf. (:configure io)"
  
  [^long iir-filter standby]

  ;; the first bit is for enabling 3-wire SPI instead of 4-wire
  ;; since we use I2C it is set to 0 and we do not care
  (bit-or (bit-shift-left (-iir-filter iir-filter)
                          1)
          (bit-shift-left (-standby standby)
                          4)))




(s/fdef control-humidity

  :args (s/cat :oversampling ::oversampling)
  :ret  ::ubyte)


(defn control-humidity

  "Prepares a byte for configuring humidity.
  
   Cf. (:control-humidity io)"

  [oversampling]

  (-oversampling oversampling))




(s/fdef control-measurements

  :args (s/cat :mode                      ::mode
               :oversampling--pressure    ::oversampling
               :oversampling--temperature ::oversampling)
  :ret  ::ubyte)


(defn control-measurements

  "Prepares a byte for configuring the mode, the pressure and the temperature.
  
   Cf. (:control-measurements io)"

  [mode oversampling--pressure oversampling--temperature]

  (bit-or (-mode mode)
          (bit-shift-left (-oversampling oversampling--pressure)
                          2)
          (bit-shift-left (-oversampling oversampling--temperature)
                          5)))




(declare humidity
         precise-temperature
         pressure
         to-celsius)




(s/fdef data

  :args (s/cat :buffer-data ::buffer.data
               :cw          ::compensation-words)
  :ret  ::data)


(defn data
  
  "Given raw data, computes and adjusts the humidity, pressure and temperature at once.
  
   Cf. (:data io)"

  [buffer-data cw]

  (let [t (precise-temperature buffer-data
                               cw)]
    {::humidity    (humidity buffer-data
                             cw
                             t)
     ::pressure    (pressure buffer-data
                             cw
                             t)
     ::temperature (to-celsius t)}))




(s/fdef duration--max

  :args (s/cat :oversampling--humidity    ::oversampling
               :oversampling--pressure    ::oversampling
               :oversampling--temperature ::oversampling)
  :ret  ::duration.max)


(defn duration--max

  "Computes the maximum duration in milliseconds of a single measure given oversamplings."

  ^double

  [oversampling--humidity oversampling--pressure oversampling--temperature]

  (let [oversampling--humidity'    (-oversampling--mult oversampling--humidity)
        oversampling--pressure'    (-oversampling--mult oversampling--pressure)
        oversampling--temperature' (-oversampling--mult oversampling--temperature)]
    (+ 1.25
       (if (pos? oversampling--humidity')
         (+ (* 2.3
               oversampling--humidity')
            0.575)
         0)
       (if (pos? oversampling--pressure')
         (+ (* 2.3
               oversampling--pressure')
            0.575)
         0)
       (* 2.3
          oversampling--temperature'))))




(s/fdef duration--typical

  :args (s/cat :oversampling--humidity    ::oversampling
               :oversampling--pressure    ::oversampling
               :oversampling--temperature ::oversampling)
  :ret  ::duration.typical)


(defn duration--typical

  "Computes the typical duration in milliseconds of a single measure given oversampling."

  ^double

  [oversampling--humidity oversampling--pressure oversampling--temperature]

  (let [oversampling--humidity'    (-oversampling--mult oversampling--humidity)
        oversampling--pressure'    (-oversampling--mult oversampling--pressure)
        oversampling--temperature' (-oversampling--mult oversampling--temperature)]
    (+ 1
       (if (pos? oversampling--humidity')
         (+ (* 2
               oversampling--humidity')
            0.5)
         0)
       (if (pos? oversampling--pressure')
         (+ (* 2
               oversampling--pressure')
            0.5)
         0)
       (* 2
          oversampling--temperature'))))




(s/fdef duration--with-iir-filter

  :args (s/cat :iir-filter ::iir-filter
               :duration   ::duration.max)
  :ret  ::duration.with-iir-filter)


(defn duration--with-iir-filter

  "Adjusts the given duration in milliseconds of a single measure by taking into account the chosen iir filter.
  
   Cf. `duration--max`
       `duration--typical`"

  ^double

  [^long iir-filter ^double duration]

  (* duration
     (case iir-filter
        0  1
        2  2
        4  5
        8 11
       16 22)))




(s/fdef humidity

  :args (s/cat :buffer-data         ::buffer.data
               :cw                  ::compensation-words
               :precise-temperature ::precise-temperature)
  :ret ::humidity)


(defn humidity

  "Computes and adjusts the humidity."

  ^double

  [buffer-data cw precise-temperature]

  (let [h     (-ushort buffer-data 7 6)
        H1    (::H1 cw) 
        H2    (::H2 cw)
        H3    (::H3 cw)
        H4    (::H4 cw)
        H5    (::H5 cw)
        H6    (::H6 cw)
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




(s/fdef precise-temperature

  :args (s/cat :buffer-data ::buffer.data
               :cw          ::compensation-words)
  :ret  ::precise-temperature)


(defn precise-temperature

  "Computes and adjusts the \"precise\" temperature which is then used for computing the humidity,
   pressure and temperature in celcius."

  ^double

  [buffer-data cw]
   
  (let [t    (-uint buffer-data 5 4 3)
        T1   (::T1 cw)
        T2   (::T2 cw)
        T3   (::T3 cw)
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




(s/fdef pressure

  :args (s/cat :buffer-data         ::buffer.data
               :cw                  ::compensation-words
               :precise-temperature ::precise-temperature)
  :ret  ::pressure)


(defn pressure

  "Computes and adjusts the pressure."

  ^double

  [buffer-data cw precise-temperature]

  (let [p    (-uint buffer-data 2 1 0)
        P1   (::P1 cw) 
        P2   (::P2 cw)
        P3   (::P3 cw)
        P4   (::P4 cw)
        P5   (::P5 cw)
        P6   (::P6 cw)
        P7   (::P7 cw)
        P8   (::P8 cw)
        P9   (::P9 cw)
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




(s/fdef status

  :args (s/cat :b ::ubyte)
  :ret  ::status)


(defn status

  "Computes the current status of the sensors.
  
   Cf. (:status io)"

  [b]

  {::copying-NVM?        (bit-test b
                                   0)
   ::running-conversion? (bit-test b
                                   3)})




(s/fdef to-celsius

  :args (s/cat :precise-temperature ::precise-temperature)
  :ret  ::temperature)


(defn to-celsius

  "Converts a \"precise\" temperature to celsius."

  ^double

  [precise-temperature]

  (-restrict-number -40
                    85
                    (/ precise-temperature
                       5120)))
