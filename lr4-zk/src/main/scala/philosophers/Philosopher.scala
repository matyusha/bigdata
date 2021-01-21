package philosophers

import java.util.concurrent.Semaphore

import org.apache.zookeeper.{CreateMode, WatchedEvent, Watcher, ZooDefs, ZooKeeper}

import scala.util.Random

case class Philosopher(id: Int,
                       hostPort: String,
                       root: String,
                       left: Semaphore,
                       right: Semaphore,
                       seats: Integer) extends Watcher {

  val zk = new ZooKeeper(hostPort, 3000, this)
  val mutex = new Object()
  val path: String = root + "/" + id.toString

  if (zk == null) throw new Exception("ZK is NULL.")

  override def process(event: WatchedEvent): Unit = {
    mutex.synchronized {
      mutex.notify()
    }
  }

  def eat(): Unit ={
    printf("Philosopher %d preparing to eat\n", id)
    mutex.synchronized {
      while (true) {
        if (zk.exists(path, false) == null) {
          zk.create(path, Array.emptyByteArray, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
        }
        val active = zk.getChildren(root, this)
        if (active.size() > seats) {
          zk.delete(path, -1)
          mutex.wait(3000)
          Thread.sleep(Random.nextInt(5) * 100)
        } else {
          left.acquire()
          printf("Philosopher %d took left fork\n", id)
          right.acquire()
          printf("Philosopher %d took right fork\n", id)
          Thread.sleep((Random.nextInt(3) + 1) * 1000)
          left.release()
          printf("Philosopher %d placed left fork back\n", id)
          right.release()
          printf("Philosopher %d placed right fork back\n", id)
          return
        }
      }
    }
  }

  def think(): Unit = {
    printf("Philosopher %d is thinking\n", id)
    zk.delete(path, -1)
    Thread.sleep((Random.nextInt(5) + 1) * 1000)
  }
}
