import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.apache.spark._
import org.apache.log4j.{Level, Logger}

case class Station(
                    stationId: Integer,
                    name: String,
                    lat: Double,
                    long: Double,
                    dockcount: Integer,
                    landmark: String,
                    installation: String,
                    notes: String)

case class Trip(tripId: Integer,
                duration: Integer,
                startDate: LocalDateTime,
                startStation: String,
                startTerminal: Integer,
                endDate: LocalDateTime,
                endStation: String,
                endTerminal: Integer,
                bikeId: Integer,
                subscriptionType: String,
                zipCode: String)

object Main {

  private def getDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) = {
    val latRad1 = lat1 * Math.PI / 180
    val lonRad1 = lon1 * Math.PI / 180
    val latRad2 = lat2 * Math.PI / 180
    val lonRad2 = lon2 * Math.PI / 180
    val cosLat1 = Math.cos(latRad1)
    val cosLat2 = Math.cos(latRad2)
    val sinLat1 = Math.sin(latRad1)
    val sinLat2 = Math.sin(latRad2)
    val delta = Math.abs(lonRad2 - lonRad1)
    val cosDelta = Math.cos(delta)
    val sinDelta = Math.sin(delta)
    val y = Math.sqrt(Math.pow(cosLat2 * sinDelta, 2) + Math.pow(cosLat1 * sinLat2 - sinLat1 * cosLat2 * cosDelta, 2))
    val x = sinLat1 * sinLat2 + cosLat1 * cosLat2 * cosDelta
    val ad = Math.atan2(y, x)
    (ad * 6372795) / 1000
  }


  def main(args: Array[String]) {
    Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
    Logger.getLogger("org.spark-project").setLevel(Level.WARN)
    val Seq(masterURL, tripDataPath, stationDataPath) = args.toSeq
    val cfg = new SparkConf().setAppName("Test").setMaster(masterURL)
    val sc = new SparkContext(cfg)

    val tripData = sc.textFile(tripDataPath)
    val trips = tripData.map(row => row.split(",", -1))
    val stationData = sc.textFile(stationDataPath)
    val stations = stationData.map(row => row.split(",", -1))

    val stationsIndexed = stations.keyBy(row => row(0).toInt)
    val tripsByStartTerminals = trips.keyBy(row => row(4).toInt)
    val tripsByEndTerminals = trips.keyBy(row => row(7).toInt)
    val startTrips = stationsIndexed.join(tripsByStartTerminals)
    val endTrips = stationsIndexed.join(tripsByEndTerminals)

    startTrips.count()
    endTrips.count()

    val tripsInternal = trips.mapPartitions(rows => {
      val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd H:m")
      rows.map(row =>
        new Trip(tripId = row(0).toInt,
          duration = row(1).toInt,
          startDate = LocalDateTime.parse(row(2), timeFormat),
          startStation = row(3),
          startTerminal = row(4).toInt,
          endDate = LocalDateTime.parse(row(5), timeFormat),
          endStation = row(6),
          endTerminal = row(7).toInt,
          bikeId = row(8).toInt,
          subscriptionType = row(9),
          zipCode = row(10)))
    })

    val stationsInternal = stations.map(row =>
      new Station(stationId = row(0).toInt,
        name = row(1),
        lat = row(2).toDouble,
        long = row(3).toDouble,
        dockcount = row(4).toInt,
        landmark = row(5),
        installation = row(6),
        notes = null))
    /*
    * Выводит информацию велосипеда с максимальным по времени использованием
    * */
    val tripsByBikeId = tripsInternal.keyBy(record => record.bikeId)

    val mileageBikeId = tripsByBikeId
      .mapValues(x => x.duration)
      .groupByKey()
      .mapValues(col => col.reduce((a, b) => a + b))
    val maxMileageBikeId = mileageBikeId.keyBy(x => x._2).max()
    println("//////////////task 1//////////////////")
    println("the bike "+ maxMileageBikeId._2._1+" with the maximum mileage "+ maxMileageBikeId._1 )

    /*
        * Выводит информацию о наибольшем расстоянии между станциями
        * */
    val stationLatLong = stationsInternal.keyBy(x => x.stationId)
      .mapValues(x => (x.name, x.lat, x.long))

    val cartesianStationLatLong = stationLatLong.cartesian(stationLatLong)
    val stationDistances = cartesianStationLatLong.map(x =>
      ((x._1._1, x._2._1), (x._1._2._1, x._2._2._1), getDistanceKm(x._1._2._2, x._1._2._3, x._2._2._2, x._2._2._3)))

    val maxStatinDistance = stationDistances.keyBy(x => x._3).max()
    println("//////////////task 2//////////////////")
    println("the greatest distance between stations " + maxStatinDistance)

    /*
        * Выводит информацию о поездках велосипеда с максимальным по времени использованием
        * */
    val tripMaxDurationBike = tripsByBikeId.filter(_._1 == maxMileageBikeId._2._1)
    println("//////////////task 3//////////////////")
    println("trip by bike with max duration")
    tripMaxDurationBike.take(tripMaxDurationBike.count().toInt).foreach(println)

    /*
    * Выводит количество велосипедов
    * */
    val countBike = tripsByBikeId.groupByKey().distinct()
    println("//////////////task 4//////////////////")
    println("count bike:")
    println(countBike.count())

    /*
    * Выводит tripId и duration где duration > 10800 сек (3 часа)
    * */
    val usersTrips = tripsInternal.keyBy(x => x.tripId)
    val timeTripsUsers = usersTrips
      .mapValues(x => (x.duration))
      .filter(x => x._2 > 10800)
    println("//////////////task 5//////////////////")
    println("trip with duration > 3 hours")
    timeTripsUsers.take(timeTripsUsers.count().toInt).foreach(println)
    sc.stop()

  }
}


