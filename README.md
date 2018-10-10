
## Join Group Algorithm

This is very simple summary of what the algorithm looks like on the Java side

1. When the consumer.poll is called, the coordinator also calls its poll method. 
2. In the coordinator.poll() method, it checks whether it needs to join the group
3. If the auto commit is enabled, it will send a syncrhonous auto-commit of offsets
4. If it needs to join the group, it sends a JoinGroupRequest to the local coordinator on the same thread
5. After receiving join group requests from all members in the group, the coordinator will select one member to be the group leader and a protocol which is supported by all members.
6. The leader will receive the full list of members along with the associated metadata for the protocol chosen. Other members, followers, will receive an empty array of members. 
7. Sends the SyncGroupRequest back to the coordinator to assign state (e.g. partition assignments) to all members of the current generation. All members send SyncGroup immediately after joining the group, but only the leader provides the group's assignment.
8. Fetches committed offsets for the assigned partitions (only one member of the consumer group)
9. Continues with the poll loop

The interesting part about the java consumers is that only one is active at the time when there is a single partition. If there are multiple partitions, the coordinator will figure out what partitions each consumer will get. 


### Ruby Kafka

Digging in the ruby kafka, it seems like the main differences are:

1. No synchronous commit of offsets prior to sending JoinGroupRequest
2. Java Consumer uses a RangeAssignor while the Ruby Consumer uses a RoundRobinAsignor
3. Everything is done in the main thread. The actual network client calls are run as futures but it's
synchronized by calling client.poll() which will block for a specified amount of time.
3. For a single partition, in the Java client, if a new Consumer joins, and it is selected based on the partition assignment, it will start consuming messages from the last committed offset. If new Consumer is not chosen, the existing Consumer will continue from the last committed offset. However, in the ruby client, when a new Consumer joins, both consumers do the work.

## How to run

To build: `mvn package`

To run a consumer: `java -jar target/*.jar`
