package com.purbon.kafka.topology.integration;

import com.purbon.kafka.topology.AclsManager;
import com.purbon.kafka.topology.SASLKafkaContainer;
import com.purbon.kafka.topology.TopologyBuilderAdminClient;
import com.purbon.kafka.topology.model.Project;
import com.purbon.kafka.topology.model.Topic;
import com.purbon.kafka.topology.model.Topology;
import com.purbon.kafka.topology.model.users.Consumer;
import com.purbon.kafka.topology.model.users.Producer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AclsManagerIT {

  private static AdminClient kafkaAdminClient;
  private AclsManager aclsManager;

  private static SASLKafkaContainer container;

  @BeforeClass
  public static void setup() {

    container =  new SASLKafkaContainer("5.3.1");
    container.start();
  }

  @AfterClass
  public static void teardown() {
    container.stop();
  }

  @Before
  public void before() {
    kafkaAdminClient = AdminClient.create(config());
    TopologyBuilderAdminClient adminClient = new TopologyBuilderAdminClient(kafkaAdminClient);
    aclsManager = new AclsManager(adminClient);
  }


  @Test
  public void consumerAclsCreation() throws ExecutionException, InterruptedException {

    List<Consumer> consumers = new ArrayList<>();
    consumers.add(new Consumer("app1"));

    Project project = new Project("project");
    project.setConsumers(consumers);
    Topic topicA = new Topic("topicA");
    project.addTopic(topicA);

    Topology topology = new Topology();
    topology.setTeam("integration-test");
    topology.setSource("testConsumerAclsCreation");
    topology.addProject(project);

    aclsManager.syncAcls(topology);

    verifyConsumerAcls(consumers, topicA.composeTopicName(topology, project.getName()));
  }

  @Test
  public void producerAclsCreation() throws ExecutionException, InterruptedException {

    List<Producer> producers = new ArrayList<>();
    producers.add(new Producer("app1"));

    Project project = new Project("project");
    project.setProducers(producers);
    Topic topicA = new Topic("topicA");
    project.addTopic(topicA);

    Topology topology = new Topology();
    topology.setTeam("integration-test");
    topology.setSource("producerAclsCreation");
    topology.addProject(project);

    aclsManager.syncAcls(topology);

    verifyProducerAcls(producers, topicA.composeTopicName(topology, project.getName()));
  }

  private void verifyProducerAcls(List<Producer> producers, String topic)
      throws InterruptedException, ExecutionException {

    for (Producer producer : producers) {
      ResourcePatternFilter resourceFilter = ResourcePatternFilter.ANY;
      AccessControlEntryFilter entryFilter = new AccessControlEntryFilter(producer.getPrincipal(),
          null, AclOperation.WRITE, AclPermissionType.ALLOW);

      AclBindingFilter filter = new AclBindingFilter(resourceFilter, entryFilter);
      Collection<AclBinding> acls = kafkaAdminClient
          .describeAcls(filter)
          .values()
          .get();

      Assert.assertEquals(1, acls.size());

      List<ResourceType> types = acls
          .stream()
          .map(aclBinding -> aclBinding.pattern().resourceType())
          .collect(Collectors.toList());

      Assert.assertTrue(types.contains(ResourceType.TOPIC));
    }
  }

  private void verifyConsumerAcls(List<Consumer> consumers, String topic)
      throws InterruptedException, ExecutionException {

    for(Consumer consumer : consumers) {
      ResourcePatternFilter resourceFilter = ResourcePatternFilter.ANY;
      AccessControlEntryFilter entryFilter = new AccessControlEntryFilter(consumer.getPrincipal(), null, AclOperation.READ, AclPermissionType.ALLOW);

      AclBindingFilter filter = new AclBindingFilter(resourceFilter, entryFilter);
      Collection<AclBinding> acls = kafkaAdminClient
          .describeAcls(filter)
          .values()
          .get();

        Assert.assertEquals(2, acls.size());

        List<ResourceType> types = acls
            .stream()
            .map(aclBinding -> aclBinding.pattern().resourceType())
            .collect(Collectors.toList());

        Assert.assertTrue(types.contains(ResourceType.GROUP));
        Assert.assertTrue(types.contains(ResourceType.TOPIC));
    }

  }


  private Properties config() {
    Properties props = new Properties();

    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(AdminClientConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
    props.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
    props.put("sasl.mechanism", "PLAIN");

    props.put("sasl.jaas.config","org.apache.kafka.common.security.plain.PlainLoginModule required username=\"kafka\" password=\"kafka\";");

    return props;
  }
}
