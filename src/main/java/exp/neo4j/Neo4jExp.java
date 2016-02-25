package exp.neo4j;

import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.logging.slf4j.Slf4jLogProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.neo4j.helpers.collection.IteratorUtil.loop;

public class Neo4jExp
{
  private static final String NEO4J_TEMP_DIR = "neo4j_temp_dir";
  private static final String USER_LABEL_NAME = "User";
  private static final String USERNAME_PROPERTY_NAME = "username";

  private static enum RelTypes implements RelationshipType
  {
    KNOWS
  }

  public static void main(String[] args)
  {
    try {
      Path databaseFilePath = Files.createTempDirectory(NEO4J_TEMP_DIR);
      GraphDatabaseService graphDatabase = new GraphDatabaseFactory().setUserLogProvider(new Slf4jLogProvider())
        .newEmbeddedDatabase(databaseFilePath.toFile());
      registerShutdownHook(graphDatabase);
      createUserNodes(graphDatabase);
      findUser(graphDatabase, "34");
      deleteUser(graphDatabase, "44");
      dropIndex(graphDatabase);
      graphDatabase.shutdown();
    } catch (IOException e) {
      System.err.println("IO error opening database " + e.getMessage());
    }
  }

  private static void createHelloWorldNodes(GraphDatabaseService graphDatabase)
  {
    try (Transaction transaction = graphDatabase.beginTx()) {
      Node firstNode = graphDatabase.createNode();
      firstNode.setProperty("message", "Hello, ");
      Node secondNode = graphDatabase.createNode();
      secondNode.setProperty("message", "World!");

      Relationship relationship = firstNode.createRelationshipTo(secondNode, RelTypes.KNOWS);
      relationship.setProperty("message", "brave Neo4j");
      transaction.success();
    }
  }

  private static void query(GraphDatabaseService graphDatabase)
  {
    try (
      Transaction ignored = graphDatabase.beginTx();
      Result result = graphDatabase.execute("match (n) return n")
    )

    {
      while (result.hasNext()) {
        Map<String, Object> row = result.next();
        for (Map.Entry<String, Object> column : row.entrySet()) {
          System.out.println("key: " + column.getKey() + ", value: " + column.getValue());
        }
      }
    }
  }

  private static void createUserNodes(GraphDatabaseService graphDatabase)
  {
    try (Transaction tx = graphDatabase.beginTx()) {
      Label label = DynamicLabel.label(USER_LABEL_NAME);

      // Create some users
      for (int id = 0; id < 100; id++) {
        Node userNode = graphDatabase.createNode(label);
        userNode.setProperty(USERNAME_PROPERTY_NAME, USER_LABEL_NAME + id);
      }
      System.out.println("Users created");
      tx.success();
    }

    IndexDefinition indexDefinition;
    try (Transaction tx = graphDatabase.beginTx()) {
      Schema schema = graphDatabase.schema();
      indexDefinition = schema.indexFor(DynamicLabel.label(USER_LABEL_NAME)).on(USERNAME_PROPERTY_NAME).create();
      tx.success();
    }

    // Indexing must be in separate transaction
    try (Transaction tx = graphDatabase.beginTx()) {
      Schema schema = graphDatabase.schema();
      schema.awaitIndexOnline(indexDefinition, 10, TimeUnit.SECONDS);
    }
  }

  private static void findUser(GraphDatabaseService graphDatabase, String idToFind)
  {
    Label userLabel = DynamicLabel.label(USER_LABEL_NAME);
    String nameToFind = USER_LABEL_NAME + idToFind;
    try (Transaction tx = graphDatabase.beginTx()) {
      try (ResourceIterator<Node> users = graphDatabase.findNodes(userLabel, USERNAME_PROPERTY_NAME, nameToFind)) {
        ArrayList<Node> userNodes = new ArrayList<>();
        while (users.hasNext()) {
          userNodes.add(users.next());
        }

        for (Node node : userNodes) {
          System.out.println("The username of user " + idToFind + " is " + node.getProperty(USERNAME_PROPERTY_NAME));
        }
      }
    }
  }

  private static void deleteUser(GraphDatabaseService graphDatabase, String idToDelete)
  {
    try (Transaction tx = graphDatabase.beginTx()) {
      Label userLabel = DynamicLabel.label(USER_LABEL_NAME);
      String nameToFind = USER_LABEL_NAME + idToDelete;

      for (Node node : loop(graphDatabase.findNodes(userLabel, USER_LABEL_NAME, nameToFind))) {
        node.setProperty(USER_LABEL_NAME, USER_LABEL_NAME + (idToDelete + 1));
      }
      tx.success();
    }
  }

  private static void dropIndex(GraphDatabaseService graphDatabase)
  {
    try (Transaction tx = graphDatabase.beginTx()) {
      Label label = DynamicLabel.label(USER_LABEL_NAME);
      for (IndexDefinition indexDefinition : graphDatabase.schema().getIndexes(label)) {
        // There is only one index
        indexDefinition.drop();
      }
      tx.success();
    }
  }

  private static void registerShutdownHook(final GraphDatabaseService graphDb)
  {
    Runtime.getRuntime().addShutdownHook(new Thread()
    {
      @Override public void run()
      {
        graphDb.shutdown();
      }
    });
  }
}

