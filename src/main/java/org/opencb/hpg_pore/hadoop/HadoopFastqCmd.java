package org.opencb.hpg_pore.hadoop;

import java.io.IOException;
import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.util.Tool;
import org.opencb.hpg_pore.NativePoreSupport;

public class HadoopFastqCmd extends Configured implements Tool {
	public static class Map extends Mapper<Text, BytesWritable, NullWritable, Text> {

		private MultipleOutputs<NullWritable, Text> multipleOutputs = null;

		@Override
		public void setup(Context context) {
			NativePoreSupport.loadLibrary();
			multipleOutputs = new MultipleOutputs<NullWritable, Text>(context);
		}

		@Override
		protected void cleanup(Context context) throws IOException,
		InterruptedException {
			multipleOutputs.close();
		}		

		@Override
		public void map(Text key, BytesWritable value, Context context) throws IOException, InterruptedException {
			

			String info = new NativePoreSupport().getFastqs(value.getBytes());
			if (info == null || info.length() <= 0) {
				System.out.println("Could not read sequences from file "+ key + " . Maybe, the file does not contain any sequence or it is corrupt");
				return;
			}
			System.out.println("File " + key + ". Processed.");
			//String info = "run-2d\n@read1\nAAAA\n+\n2222";

			byte[] brLine = new byte[1];
			brLine[0] = '\n';

			String[] lines = info.split("\n");
			String name = null;
			Text content = null;

			String line;

			//System.out.println("info length = " + info.length() + ", num.lines = " + lines.length);

			for (int i = 0; i < lines.length; i += 5) {
				// first line: runId & template/complement/2d				
				line = lines[i];
				//System.out.println(i + " of " + lines.length + " : " + line);
				name = new String(line + "/file");

				// second line: read ID
				line = lines[i + 1];
				content = new Text(line + "\n");

				// third line: nucleotides
				line = lines[i + 2];
				content.append(line.getBytes(), 0, line.length());
				content.append(brLine, 0, 1);

				// fourth line: +
				line = lines[i + 3];
				content.append(line.getBytes(), 0, line.length());
				content.append(brLine, 0, 1);

				// fifth line: qualities
				line = lines[i + 4];
				content.append(line.getBytes(), 0, line.length());
				//content.append(brLine, 0, 1);

				multipleOutputs.write(NullWritable.get(), content, name);
				multipleOutputs.close();
			}
		}
	}

	@Override
	public int run(String[] args) throws Exception {
		Configuration conf = new Configuration();

		Job job = new Job(conf, "hpg-pore-fastq");
		job.setJarByClass(HadoopFastqCmd.class);

		job.addCacheFile(new Path(NativePoreSupport.LIB_FULLNAME).toUri());

		String srcFileName = args[0];
		String outDirName = args[1];

		// add input files to mapreduce processing
		FileInputFormat.addInputPath(job, new Path(srcFileName + "/data"));
		job.setInputFormatClass(SequenceFileInputFormat.class);

		// set output file
		FileOutputFormat.setOutputPath(job, new Path(outDirName));

		// set map, combine, reduce...
		job.setMapperClass(Map.class);
		job.setNumReduceTasks(0);
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(Text.class);

		return (job.waitForCompletion(true) ? 0 : 1);
	}
}
