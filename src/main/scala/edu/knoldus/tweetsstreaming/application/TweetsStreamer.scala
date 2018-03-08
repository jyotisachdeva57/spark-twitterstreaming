package edu.knoldus.tweetsstreaming.application

import java.sql.DriverManager
import org.apache.spark._
import org.apache.spark.streaming._
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.twitter._
import twitter4j.Status


object TweetsStreamer {

  def main(args: Array[String]) {

    val conf = new SparkConf().setMaster("local[2]").setAppName("SparkTwitter")
    val sc = new SparkContext(conf)
    val jssc = new StreamingContext(sc, Seconds(5))
    val twitterStream: DStream[Status] = TwitterUtils.createStream(jssc, None)


    val statuses = twitterStream.map(status => status.getText)
    statuses.print()
    jssc.checkpoint("_checkpoint")

    val tweetWords = statuses.flatMap(tweetText => tweetText.split(" "))

    val hashTags = tweetWords.filter(word => word.startsWith("#"))

    val topCounts60 = hashTags.map((_, 1)).reduceByKeyAndWindow(_ + _, Seconds(60))
      .map { case (topic, count) => (count, topic) }
      .transform(_.sortByKey(ascending = false))


    topCounts60.foreachRDD(rdd => {
      val topList = rdd.take(3)
      println("\nPopular topics in last 60 seconds (%s total):".format(rdd.count()))
      topList.foreach { case (count, tag) => println("%s (%s tweets)".format(tag, count)) }
    })


    topCounts60.foreachRDD {
      rdd =>
        rdd.take(3).foreach {
          case (count, hashTag) =>
            Class.forName("com.mysql.jdbc.Driver")
            val conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/tweetDB","root","root")
            val del = conn.prepareStatement("INSERT INTO  Tweets (count,tweets) VALUES (?,?)")
            del.setInt(1, count)
            del.setString(2, hashTag)
            del.executeUpdate
            conn.close()
        }
    }

    jssc.start()
    jssc.awaitTermination()
  }

}
