/**
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;

/**
 * Split the Reuters SGML documents into Simple Text files containing: Title,
 * Date, Dateline, Body
 */
public class ExtractReuters {
	private File reutersDir;
	
	private SolrServer server;

	/*
	 * private File outputDir; private static final String LINE_SEPARATOR =
	 * System .getProperty("line.separator");
	 */

	public ExtractReuters(File reutersDir) {
		this.reutersDir = reutersDir;
		server = new HttpSolrServer("http://localhost:8983/solr/core0");
		
		/*
		 * this.outputDir = outputDir;
		 * System.out.println("Deleting all files in " + outputDir); for (File f
		 * : outputDir.listFiles()) { f.delete(); }
		 */
	}

	public void extract() {
		File[] sgmFiles = reutersDir.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.getName().endsWith(".sgm");
			}
		});
		if (sgmFiles != null && sgmFiles.length > 0) {
			for (File sgmFile : sgmFiles) {
				extractFile(sgmFile);
			}
		} else {
			System.err.println("No .sgm files in " + reutersDir);
		}
	}

	/*
	 * private static String[] META_CHARS = { "&", "<", ">", "\"", "'" };
	 * 
	 * private static String[] META_CHARS_SERIALIZATIONS = { "&amp;", "&lt;",
	 * "&gt;", "&quot;", "&apos;" };
	 */
	/**
	 * Override if you wish to change what is extracted
	 * 
	 * @param sgmFile
	 */
	protected void extractFile(File sgmFile) {
		try {

			List<Pattern> patterns = new ArrayList<>();
			List<Pattern> multivaluedPatterns = new ArrayList<>();

			Pattern TITLE_PATTERN = Pattern.compile("<TITLE>(.*?)</TITLE>");
			patterns.add(TITLE_PATTERN);

			Pattern DATE_PATTERN = Pattern.compile(" <DATE>(.*?)</DATE>");
			patterns.add(DATE_PATTERN);

			Pattern BODY_PATTERN = Pattern.compile("<BODY>(.*?)</BODY>");
			patterns.add(BODY_PATTERN);

			Pattern DATELINE_PATTERN = Pattern
					.compile("<DATELINE>(.*?)</DATELINE>");
			patterns.add(DATELINE_PATTERN);

			Pattern UNKNOWN_PATTERN = Pattern
					.compile("<UNKNOWN>(.*?)</UNKNOWN>");
			patterns.add(UNKNOWN_PATTERN);

			Pattern ORGS_PATTERN = Pattern.compile("<ORGS>(.*?)</ORGS>");
			multivaluedPatterns.add(ORGS_PATTERN);

			Pattern TOPICS_PATTERN = Pattern.compile("<TOPICS>(.*?)</TOPICS>");
			multivaluedPatterns.add(TOPICS_PATTERN);

			Pattern PLACES_PATTERN = Pattern.compile("<PLACES>(.*?)</PLACES>");
			multivaluedPatterns.add(PLACES_PATTERN);

			Pattern PEOPLE_PATTERN = Pattern.compile("<PEOPLE>(.*?)</PEOPLE>");
			multivaluedPatterns.add(PEOPLE_PATTERN);

			Pattern EXCHANGES_PATTERN = Pattern
					.compile("<EXCHANGES>(.*?)</EXCHANGES>");
			multivaluedPatterns.add(EXCHANGES_PATTERN);

			Pattern COMPANIES_PATTERN = Pattern
					.compile("<COMPANIES>(.*?)</COMPANIES>");
			multivaluedPatterns.add(COMPANIES_PATTERN);

			Pattern D_PATTERN = Pattern.compile("<D>(.*?)</D>");

			BufferedReader reader = new BufferedReader(new FileReader(sgmFile));

			StringBuilder buffer = new StringBuilder(10024);
			//StringBuilder outBuffer = new StringBuilder(1024);

			String line = null;
			int docNumber = 0;
			SolrInputDocument document = null;

			List<SolrInputDocument> docs = new ArrayList<>();
			int count  = 0;
			while ((line = reader.readLine()) != null) {
				// when we see a closing reuters tag, flush the file

				if (line.indexOf("</REUTERS") == -1) {
					// Replace the SGM escape sequences

					buffer.append(line).append(' ');// accumulate the strings

					if (line.contains("<REUTERS")) {
						document = new SolrInputDocument();
						line = line.replace("<REUTERS", "");
						line = line.replace(">", "");
						String[] headers = line.trim().split(" ");
						for (String header : headers) {
							String head[] = header.split("=");
							document.addField(head[0],
									head[1].replace("\"", ""));
							System.out.println(header);
						}

					}

				} else {

					for (Pattern pattern : patterns) {
						Matcher matcher = pattern.matcher(buffer);
						while (matcher.find()) {
							for (int i = 1; i <= matcher.groupCount(); i++) {
								if (matcher.group(i) != null) {
									String string = pattern.pattern();
									String field = string.substring(
											string.indexOf("<") + 1,
											string.indexOf(">")).trim();
									System.out.println(field + "  : "
											+ matcher.group(i));
									document.addField(field, matcher.group(i));

								}
							}
						}
					}

					for (Pattern pattern : multivaluedPatterns) {
						Matcher matcher = pattern.matcher(buffer);
						while (matcher.find()) {
							for (int i = 1; i <= matcher.groupCount(); i++) {
								if (matcher.group(i) != null) {
									String text = matcher.group(i);
									String string = pattern.pattern();
									String field = string.substring(
											string.indexOf("<") + 1,
											string.indexOf(">")).trim();
									System.out.println(field + "  : " + text);
									if (text != null && !text.trim().isEmpty()) {
										List<String> values = new ArrayList<>();

										Matcher mat = D_PATTERN.matcher(text);
										while (mat.find()) {
											for (int j = 1; j <= mat
													.groupCount(); j++) {
												if (mat.group(j) != null) {

													System.out.println(mat
															.group(j));
													values.add(mat.group(j));
												}
											}
										}
										document.addField(field, values);
									}

								}
							}
						}
					}

					//add
					docs.add(document);
					count++;
					if(count == 500) {
						server.add(docs);
						server.commit();
						docs.clear();
						count = 0;
						
					}
					buffer = new StringBuilder(10024);
					System.out.println();
				}
			}
				if(!docs.isEmpty()) {
				server.add(docs);
				server.commit();
			}
			
			reader.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			usage("Wrong number of arguments (" + args.length + ")");
			return;
		}
		File reutersDir = new File(args[0]);
		if (!reutersDir.exists()) {
			usage("Cannot find Path to Reuters SGM files (" + reutersDir + ")");
			return;
		}

		/*
		 * // First, extract to a tmp directory and only if everything succeeds,
		 * // rename // to output directory. File outputDir = new File(args[1]);
		 * outputDir = new File(outputDir.getAbsolutePath() + "-tmp");
		 * outputDir.mkdirs();
		 */
		ExtractReuters extractor = new ExtractReuters(reutersDir);
		extractor.extract();
		// Now rename to requested output dir
		// outputDir.renameTo(new File(args[1]));
	}

	private static void usage(String msg) {
		System.err.println("Usage: " + msg
				+ " :: java ExtractReuters <Path to Reuters SGM files>");
	}

}