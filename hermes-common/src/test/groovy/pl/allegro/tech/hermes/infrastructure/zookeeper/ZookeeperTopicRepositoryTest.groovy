package pl.allegro.tech.hermes.infrastructure.zookeeper

import pl.allegro.tech.hermes.api.Group
import pl.allegro.tech.hermes.api.Topic
import pl.allegro.tech.hermes.api.TopicName
import pl.allegro.tech.hermes.domain.group.GroupNotExistsException
import pl.allegro.tech.hermes.domain.topic.TopicAlreadyExistsException
import pl.allegro.tech.hermes.domain.topic.TopicNotEmptyException
import pl.allegro.tech.hermes.domain.topic.TopicNotExistsException
import pl.allegro.tech.hermes.test.IntegrationTest

import static pl.allegro.tech.hermes.api.Subscription.Builder.subscription
import static pl.allegro.tech.hermes.api.Topic.Builder.topic

class ZookeeperTopicRepositoryTest extends IntegrationTest {

    private static final String GROUP = "topicRepositoryGroup"

    private ZookeeperTopicRepository repository = new ZookeeperTopicRepository(zookeeper(), mapper, paths, groupRepository)

    void setup() {
        if (!groupRepository.groupExists(GROUP)) {
            groupRepository.createGroup(Group.from(GROUP))
        }
    }

    def "should create topic"() {
        when:
        repository.createTopic(topic().withName(GROUP, 'create').build())
        wait.untilTopicCreated(GROUP, 'create')

        then:
        repository.listTopicNames(GROUP).containsAll(['create'])
    }

    def "should throw exception when trying to create topic that already exists"() {
        given:
        repository.createTopic(topic().withName(GROUP, 'duplicate').build())
        wait.untilTopicCreated(GROUP, 'duplicate')

        when:
        repository.createTopic(topic().withName(GROUP, 'duplicate').build())

        then:
        thrown(TopicAlreadyExistsException)
    }

    def "should throw exception when trying to create topic for unknown group"() {
        when:
        repository.createTopic(topic().withName('unknownGroup', 'unknown').build())

        then:
        thrown(GroupNotExistsException)
    }

    def "should list all topic names"() {
        given:
        repository.createTopic(topic().withName(GROUP, 'listName1').build())
        repository.createTopic(topic().withName(GROUP, 'listName2').build())
        wait.untilTopicCreated(GROUP, 'listName1')
        wait.untilTopicCreated(GROUP, 'listName2')

        when:
        List topics = repository.listTopicNames(GROUP)

        then:
        topics.containsAll(['listName1', 'listName2'])
    }

    def "should list all topics"() {
        given:
        Topic topic1 = topic().withName(GROUP, 'list1').build()
        Topic topic2 = topic().withName(GROUP, 'list2').build()
        repository.createTopic(topic1)
        repository.createTopic(topic2)

        wait.untilTopicCreated(GROUP, 'list1')
        wait.untilTopicCreated(GROUP, 'list2')


        when:
        List topics = repository.listTopics(GROUP)

        then:
        topics.containsAll([topic1, topic2])
    }

    def "should load topic details"() {
        given:
        repository.createTopic(topic().withName(GROUP, 'details').withDescription('description').withValidation(true).build())
        wait.untilTopicCreated(GROUP, 'details')

        when:
        Topic retrievedTopic = repository.getTopicDetails(new TopicName(GROUP, 'details'))

        then:
        retrievedTopic.description == 'description'
        retrievedTopic.validationEnabled
    }

    def "should update topic"() {
        given:
        repository.createTopic(topic().withName(GROUP, 'update').withDescription('before update').build())
        wait.untilTopicCreated(GROUP, 'update')

        Topic modifiedTopic = topic().withName(GROUP, 'update').withDescription('after update').build()

        when:
        repository.updateTopic(modifiedTopic)

        then:
        repository.getTopicDetails(new TopicName(GROUP, 'update')).description == 'after update'
    }

    def "should throw exception when listing topics from unexistent group"() {
        when:
        repository.listTopics('unknownGroup')

        then:
        thrown(GroupNotExistsException)
    }

    def "should throw exception when fetching details of unexistent topic"() {
        when:
        repository.getTopicDetails(new TopicName(GROUP, 'unknown'))

        then:
        thrown(TopicNotExistsException)
    }

    def "should return empty list when no topics defined for group"() {
        when:
        groupRepository.createGroup(Group.from('emptyGroup'))

        then:
        repository.listTopics('emptyGroup') == []
    }

    def "should remove topic"() {
        given:
        repository.createTopic(topic().withName(GROUP, 'remove').build())
        wait.untilTopicCreated(GROUP, 'remove')

        when:
        repository.removeTopic(new TopicName(GROUP, 'remove'))

        then:
        !repository.topicExists(new TopicName(GROUP, 'remove'))
    }

    def "should throw exception when trying to remove topic with subscriptions"() {
        given:
        repository.createTopic(topic().withName(GROUP, 'removeWithSubscriptions').build())
        wait.untilTopicCreated(GROUP, 'removeWithSubscriptions')
        subscriptionRepository.createSubscription(subscription()
                .withTopicName(GROUP, 'removeWithSubscriptions')
                .withName('ups').build())
        wait.untilSubscriptionCreated(new TopicName(GROUP, 'removeWithSubscriptions'), 'ups')

        when:
        repository.removeTopic(new TopicName(GROUP, 'removeWithSubscriptions'))

        then:
        thrown(TopicNotEmptyException)
    }
}
