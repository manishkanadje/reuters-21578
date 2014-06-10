# README #

### What is this repository for? ###
This repository has the which implements the Naive Bayes algorithm and Nearest Neighbor algorithm for Reuters-21578 dataset. It classifies the given text document into predefined categories.

### How do I get set up? ###
For running this code you need Apache Solr Server up and running. The Solr server can be installed from "http://lucene.apache.org/solr/". The tutorial for running the Solr server can be obtained from "http://lucene.apache.org/solr/4_8_1/tutorial.html". The code is written in Java. It also requires the jar files containing the Solr API. These files are present in the 'dist' folder of the Solr installation folder.

1. Install the Apache Solr server if you don't have it as described above.
2. Replace the schema and stopword files in the Solr-4.7.1/example/solr/collection/conf with the schema
   and stopword files present in tis folder. If you create a different folder for collection, use path
   of that folder. 
3. Compile and run the ExtractReuters.java file. This file will upload the and index the Reuters data 
   into the Solr server.
4. Compile and run StemWords.java file.
5. By default the code displays results obtained using Naive Bayes algorithm. The results are displayed 
   for entire test dataset.
   If you want topic wise results, 
   a. Go to line 126 and comment out the for loop and comment out line 127
   b. Change the query in line 128 according to topic. This will display the topic wise result.
6. To use Nearest Neighbor algorithm, substitute function at line 149 with function at 153.
7. Changing the query present at line 128, will produce different results.