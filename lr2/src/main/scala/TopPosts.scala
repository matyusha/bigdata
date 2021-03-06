import org.apache.spark.{SparkContext, SparkConf}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession

object TopPosts {
  def main(args: Array[String]): Unit = {
    System.setProperty("hadoop.home.dir", "C:\\hadoop\\3.3.0\\");
    Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
    Logger.getLogger("org.spark-project").setLevel(Level.WARN)

    val Seq(masterURL, data_path_posts, data_path_program_language) = args.toSeq
    val cfg = new SparkConf().setAppName("Test").setMaster(masterURL)
    val sc = new SparkContext(cfg)
    sc.setLogLevel("error")
    val spark = SparkSession.builder().getOrCreate()

    val years = 2010 to 2021 map(_.toString)
    val topCount = 10

    val postsRDD = sc.textFile(data_path_posts)
    val posts_count = postsRDD.count
    val posts_raw = postsRDD.zipWithIndex.filter{ case (s, idx) => idx>2 && idx<posts_count-1 }.map(_._1)

    val programming_languages_raw = sc.textFile(data_path_program_language).zipWithIndex.filter{
      case (row, idx) => idx>1
    }.map{
      case (row, idx) => row
    }
    val programming_languages = programming_languages_raw.map{
      row => row.split(",")
    }.filter{
      rowValues => rowValues.size==2
    }.map{
      rowValues =>
        val Seq(name, link) = rowValues.toSeq
        name.toLowerCase
    }.collect()

    val posts_xml = posts_raw.map(row => scala.xml.XML.loadString(row))

    def extractCreationDateAndTags(e: scala.xml.Elem) = {
      val creationDate = e.attribute("CreationDate")
      val tags = e.attribute("Tags")
      (creationDate, tags)
    }
    val postCreationDateAndTags = posts_xml.map(extractCreationDateAndTags).filter {
      x => x._1.isDefined && x._2.isDefined
    }.map{
      x => (x._1.mkString, x._2.mkString)
    }

    def parseCreationDateAndTags(e:(String, String)) = {
      val (creationDate, tags) = e
      val year = creationDate.substring(0, 4)
      val tagsArray = tags.substring(4, tags.length-4).split("&gt;&lt;")
      (year, tagsArray)
    }
    val postYearTags = postCreationDateAndTags.map(parseCreationDateAndTags)

    val yearTags = postYearTags.flatMap{ case (year, tags) => tags.map(tag => (year, tag))}
    val yearLanguageTags = yearTags.filter{ case (year, tag) => programming_languages.contains(tag) }.cache()

    val yearsTagCounts = years.map{ reportYear =>
      yearLanguageTags.filter{
        case (tagYear, tag) => reportYear==tagYear
      }.map{
        case (tagYear, tag) => (tag, 1)
      }.reduceByKey{
        (a, b) => a + b
      }.map{ case (tag, count) =>
        (reportYear, tag, count)
      }
    }


    val topYearsTagCounts = yearsTagCounts.map{ yearTagsCounts =>
      yearTagsCounts.sortBy{ case (year, tag, count) => -count }.take(topCount)
    }

    val finalReport = topYearsTagCounts.reduce((a, b) => a.union(b))

    import spark.implicits._
    val finalDataFrame = sc.parallelize(finalReport).toDF("year", "language", "count")
    finalDataFrame.show(years.size*topCount)
  }
}
