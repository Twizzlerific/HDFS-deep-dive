import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;

public class fileLease {

    private static final Logger log = Logger.getLogger(fileLease.class);
    // =================================================================
    // Variables
    // --> CHANGE ME <--
    final private static String PRINCIPAL = "pokedex@KS.COM";
    final private static String KEYTAB = "/opt/phxTest/pokedex.keytab";
    final private static String LOGIN_USER = "pokedex";
    final private static Long my_date = Instant.now().getEpochSecond();
    // =================================================================



    public static void main(String[] args) throws IOException {
        Configuration conf = new Configuration();
        conf.addResource(new Path("file:///etc/hadoop/conf/hdfs-site.xml"));
        conf.addResource(new Path("file:///etc/hadoop/conf/core-site.xml"));
        FileSystem fs = FileSystem.get(conf);
        Path temp_path = new Path("/tmp/fileLease_" + my_date);
        System.out.println("Holing lease on " + temp_path.toString() + "\n Starting at: " + my_date.toString());

        try {
            FSDataOutputStream output = fs.create(temp_path);
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            byte[] arr = new byte[10];
            Random rd = new Random();
            rd.nextBytes(arr);
            System.out.println(Arrays.toString(arr));
            bufferedWriter.write(Arrays.toString(arr));
            bufferedWriter.close();
            System.out.println("Writer closed at: " + Instant.now().toString());
            System.out.println("\n" );
            System.out.println("FileSystem has not been closed" );
            System.out.println("This should result in fileLease being kept open" );


        } catch (Exception e) {
            System.out.println("Running Since: " + my_date.toString());
            System.exit(1);
        }

    }
}
