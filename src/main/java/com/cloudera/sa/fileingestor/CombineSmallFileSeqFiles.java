package com.cloudera.sa.fileingestor;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.SnappyCodec;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.log4j.Logger;

import com.cloudera.sa.fileingestor.action.AbstractIngestToHDFSAction;

public class CombineSmallFileSeqFiles {
  
  static Logger logger = Logger.getLogger(CombineSmallFileSeqFiles.class);
  
  
  public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
    
    if (args.length == 0) {
      System.out.println("CombineSmallFileSeqFiles");
      System.out.println();
      System.out.println("CombineSmallFileSeqFiles {inputPath} {outputPath} {numberOfReducers}");
      return;
    }
    
    String inputPath = args[0];
    String outputPath = args[1];
    int numberOfReducers = Integer.parseInt(args[2]);
    
    // Create job
    Job job = new Job();

    job.setJarByClass(CombineSmallFileSeqFiles.class);
    job.setJobName("CombineSmallFileSeqFiles");

    // Define input format and path
    job.setInputFormatClass(SequenceFileInputFormat.class);
    SequenceFileInputFormat.addInputPath(job, new Path(inputPath));

    // Define output format and path
    job.setOutputFormatClass(SequenceFileOutputFormat.class);
    SequenceFileOutputFormat.setOutputPath(job, new Path(outputPath));
    SequenceFileOutputFormat.setOutputCompressorClass(job, SnappyCodec.class);

    // Define the mapper and reducer
    job.setMapperClass(CustomMapper.class);
    job.setReducerClass(CustomReducer.class);

    // Define the key and value format
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(BytesWritable.class);
    job.setMapOutputKeyClass(Text.class);
    job.setMapOutputValueClass(BytesWritable.class);

    job.setNumReduceTasks(numberOfReducers);

    // Exit
    job.waitForCompletion(true);
    
    //�REMOVE LOG FILES IN HDFS
    FileSystem fs = FileSystem.get(new Configuration());
    Path dstPath = new Path(outputPath);
    try {

      for (FileStatus fileStatus : fs.listStatus(dstPath)) {
        if (fileStatus.getPath().getName().startsWith("_SUCCESS") || fileStatus.getPath().getName().startsWith("_logs")) {
          logger.info("Removing Log: " + fileStatus.getPath().getName());
          fs.delete(fileStatus.getPath(), true);
        }
      }
    } catch (Exception e) {
      logger.error("Problem removing logs.", e);
    }

  }
  
  public static class CustomMapper extends Mapper<Text, BytesWritable, Text, BytesWritable> {
    
    long isFirst = 0;
    
    @Override
    public void map(Text key, BytesWritable value, Context context) throws IOException, InterruptedException {
      
      if (isFirst++ < 100) {
        System.out.println("key:" + key.toString());
      }
      
      long startTime = System.currentTimeMillis();
      context.write(key, value);
      context.getCounter("custom", "write.context.time").increment(System.currentTimeMillis() - startTime);
    }
  }
  
  public static class CustomReducer extends Reducer<Text, BytesWritable, Text, BytesWritable> {
    @Override
    public void reduce(Text key, Iterable<BytesWritable> values, Context context) throws IOException, InterruptedException {
      for (BytesWritable value: values) {
        context.write(key, value);
      }
    }
  }
}
