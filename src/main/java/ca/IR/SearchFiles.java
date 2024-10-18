package ca.IR;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class SearchFiles {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: SearchFiles <indexDir> <queriesFile> <scoreType> <outputFile>");
            return;
        }

        String indexDir = args[0];
        String queriesFile = args[1];
        int scoreType = Integer.parseInt(args[2]);
        String outputFile = args[3];

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
                PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            setBM25Similarity(searcher);

            EnglishAnalyzer analyzer = new EnglishAnalyzer();
            String[] fields = { "title", "author", "contents" };

            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 4.0f);
            boosts.put("author", 3.0f);
            boosts.put("contents", 1.0f);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);

            File queryFile = new File(queriesFile);
            try (Scanner scanner = new Scanner(queryFile)) {
                int queryNum = 1;

                while (scanner.hasNextLine()) {
                    String queryString = scanner.nextLine().trim();
                    if (queryString.isEmpty())
                        continue;

                    try {
                        queryString = QueryParser.escape(queryString);
                        Query query = parser.parse(queryString);

                        ScoreDoc[] hits = searcher.search(query, 100).scoreDocs;

                        int rank = 1;
                        for (ScoreDoc hit : hits) {
                            Document doc = searcher.doc(hit.doc);
                            String docID = doc.get("documentID");
                            writer.println(queryNum + " 0 " + docID + " " + rank + " " + hit.score + " STANDARD");
                            rank++;
                        }
                        queryNum++;
                    } catch (Exception e) {
                        System.out.println("Error parsing query: " + queryString);
                    }
                }
            }
        }
    }

    // Setting the BM25 similarity with fine-tuned parameters
    private static void setBM25Similarity(IndexSearcher searcher) {
        BM25Similarity bm25 = new BM25Similarity(2.0f, 0.4f);
        searcher.setSimilarity(bm25);
    }
}
