package object db {

  //type DbRef[A] = Long @@ RefLink[A]
  type DbRef[A] = Long

  type InsertFunc[M] = (ObjId[M], ObjTimestamp) => M
}
