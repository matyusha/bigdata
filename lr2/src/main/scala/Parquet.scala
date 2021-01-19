import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SparkSession
import java.time.LocalDateTime
import java.sql.Timestamp
import java.time.format.DateTimeFormatter

object Parquet {
  def main(args: Array[String]): Unit = {
    System.setProperty("hadoop.home.dir", "C:\\hadoop\\3.3.0\\");
    Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
    Logger.getLogger("org.spark-project").setLevel(Level.WARN)

    val Seq(masterURL, data_path_posts) = args.toSeq
    val cfg = new SparkConf().setAppName("Test").setMaster(masterURL)
    val sc = new SparkContext(cfg)
    sc.setLogLevel("error")
    val spark = SparkSession.builder().getOrCreate()

    val postsRDD = sc.textFile(data_path_posts)
    val posts_count = postsRDD.count
    val posts_rows = postsRDD.zipWithIndex.filter{ case (s, idx) => idx>2 && idx<posts_count-1 }.map(_._1)

    val xmlRows = posts_rows.map(x =>  scala.xml.XML.loadString(x))

    val rowsInternal = xmlRows.mapPartitions(rows =>{
      val timeFormat =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
      rows.map(row =>
        new Row(
          Id = try{row.attributes("Id").text.toInt} catch {case ex: NullPointerException => 0},
          PostTypeId = try{row.attributes("PostTypeId").text.toInt} catch {case ex: NullPointerException => 0},
          CreationDate = Timestamp.valueOf(try {LocalDateTime.parse(row.attributes("CreationDate").text, timeFormat)} catch {case ex: NullPointerException => LocalDateTime.now()}),
          Score = try{row.attributes("Score").text.toInt} catch {case ex: NullPointerException => 0},
          ViewCount =try{row.attributes("ViewCount").text.toInt} catch {case ex: NullPointerException => 0},
          Body = try{row.attributes("Body").text} catch {case ex: NullPointerException => "null"},
          OwnerUserId = try{row.attributes("OwnerUserId").text.toInt} catch {case ex: NullPointerException => 0},
          LastEditorUserId = try{row.attributes("LastEditorUserId").text.toInt} catch {case ex: NullPointerException => 0},
          LastEditorDisplayName = try{row.attributes("LastEditorDisplayName").text} catch {case ex: NullPointerException => "null"},
          LastEditDate = Timestamp.valueOf(try{LocalDateTime.parse( row.attributes("LastEditDate").text, timeFormat)} catch {case ex: NullPointerException => LocalDateTime.now()}),
          LastActivityDate = Timestamp.valueOf(try{LocalDateTime.parse( row.attributes("LastActivityDate").text, timeFormat)} catch {case ex: NullPointerException => LocalDateTime.now()}),
          Title =try{row.attributes("Title").text} catch {case ex: NullPointerException => "null"},
          Tags = try{row.attributes("Tags").text} catch {case ex: NullPointerException => "null"},
          AnswerCount = try{row.attributes("AnswerCount").text.toInt} catch {case ex: NullPointerException => 0},
          CommentCount = try{row.attributes("CommentCount").text.toInt} catch {case ex: NullPointerException => 0},
          FavoriteCount = try{row.attributes("FavoriteCount").text.toInt} catch {case ex: NullPointerException => 0},
          CommunityOwnedDate = Timestamp.valueOf(try{LocalDateTime.parse(row.attributes("CommunityOwnedDate").text, timeFormat)} catch {case ex: NullPointerException => LocalDateTime.now()})
        )
      )
    })

    import spark.implicits._
    val dataset = spark.createDataset(rowsInternal)
    dataset.show(10)
    dataset.write.parquet("dataset")
  }
}
