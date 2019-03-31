import java.util.Random
import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.graphx._
import org.apache.spark.graphx.impl.GraphImpl
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.storage.StorageLevel
import breeze.linalg.{DenseVector => BDV, SparseVector => BSV, Vector => BV, StorageVector}
import org.apache.spark.mllib.linalg.{DenseVector => SDV, SparseVector => SSV,
  DenseMatrix => SDM, SparseMatrix => SSM, Vector => SV, Matrix}

object BreezeConverters {
    implicit def toBreeze( dv: SDV ): BDV[Double] =
        new BDV[Double](dv.values)

    implicit def toBreeze( sv: SSV ): BSV[Double] =
        new BSV[Double](sv.indices, sv.values, sv.size)

    implicit def toBreeze( v: SV ): BV[Double] =
        v match {
            case dv: SDV => toBreeze(dv)
            case sv: SSV => toBreeze(sv)
        }
}

object GraphXPlayGround {
  type ED = Array[Int]
  type VD = StorageVector[Int]

  def main(args: Array[String]) {
    val storageLevel: StorageLevel = StorageLevel.MEMORY_AND_DISK
    val conf: SparkConf = new SparkConf().setAppName("GraphXPlayGround").setMaster("local")
    val sc: SparkContext = new SparkContext(conf)

    val rawDocumentRdd: RDD[String] = sc.textFile("mydocument.libsvm")

    val maxIndex: Int = rawDocumentRdd.map(_.split("\t")).map {
      case Array(docId, _) => docId.toInt
    }.reduce(_ max _) + 1

    val vectorDocumentRdd: RDD[(Int, SV)] = rawDocumentRdd.map(_.split("\t")).map {
      case Array(docId, rawBody) => {
        val tokenIdArray: Array[Int] = rawBody.split(" ").map {
	  tokenIdCount => tokenIdCount.split(":").head.toInt
        }
        val tokenCountArray: Array[Double] = rawBody.split(" ").map {
	  tokenIdCount => tokenIdCount.split(":").last.toDouble
        }
        (docId.toInt, Vectors.sparse(maxIndex, tokenIdArray, tokenCountArray))
      }
    }

    val numTopics = 10
    val edges = vectorDocumentRdd.mapPartitionsWithIndex { case (pid, iter) =>
      val gen = new Random(pid)
      iter.flatMap {
        case (docId, doc) =>
          initializeEdges(gen, doc, docId, numTopics)
      }
    }
    val graph: Graph[VD, ED] = Graph.fromEdges(edges, null, storageLevel, storageLevel)
  }

  private def initializeEdges(gen: Random, doc: SV, docId: Int, numTopics: Int): Iterator[Edge[ED]] = {
    import BreezeConverters._
    val newDocId: Int = -(docId + 1)

    doc.activeIterator.filter { case (tokenId, tokenCount) => tokenCount > 0 }
      .map { case (tokenId, tokenCount) =>
        val topics = new Array[Int](tokenCount.toInt)
        for (i <- 0 until tokenCount.toInt) {
          topics(i) = gen.nextInt(numTopics)
        }
        Edge(tokenId, newDocId, topics)
      }
  }
}
