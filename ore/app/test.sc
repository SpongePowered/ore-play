import scala.util.Random

val sorted   = 0 to 10
val unsorted = Random.shuffle(sorted.toVector).map(n => (n, n.toString))

for {
  t2 <- sorted
  t1 <- unsorted
  if t1._1 == t2
} yield t1
