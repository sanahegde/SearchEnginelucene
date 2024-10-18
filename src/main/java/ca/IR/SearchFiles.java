package ca.IR;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
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

        String indexDirectory = args[0];
        String queriesFile = args[1];
        int scoringMethod = Integer.parseInt(args[2]);
        String outputFile = args[3];

        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDirectory)));
                PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            applyScoringMethod(searcher, scoringMethod);

            StandardAnalyzer analyzer = new StandardAnalyzer();
            String[] searchableFields = { "title", "contents" };
            Map<String, Float> fieldBoosts = new HashMap<>();
            fieldBoosts.put("title", 3.0f); // Boost for title
            fieldBoosts.put("contents", 1.0f);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(searchableFields, analyzer, fieldBoosts);

            // Parse and process the queries from the file
            handleQueries(queriesFile, parser, searcher, writer);
        }

        System.out.println("Search completed. Results written to: " + outputFile);
    }

    // Parse the queries from the file
    private static void handleQueries(String queryFilePath, MultiFieldQueryParser queryParser, IndexSearcher searcher,
            PrintWriter resultWriter) {
        File queryFile = new File(queryFilePath);

        if (!queryFile.exists() || !queryFile.canRead()) {
            System.out.println("Query file not found or cannot be read: " + queryFilePath);
            return;
        }

        try (Scanner scanner = new Scanner(queryFile)) {
            int queryID = 1;
            StringBuilder queryContent = new StringBuilder();
            boolean isReadingQuery = false;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                if (line.startsWith(".I")) {
                    // Process previous query content if available
                    if (isReadingQuery) {
                        executeQuery(queryContent.toString(), queryID, queryParser, searcher, resultWriter);
                        queryContent.setLength(0);
                    }
                    queryID++; // Move to the next query
                    isReadingQuery = false;
                } else if (line.startsWith(".W")) {
                    isReadingQuery = true;
                } else if (isReadingQuery) {
                    queryContent.append(line).append(" ");
                }
            }
            // Process the final query if there is content
            if (queryContent.length() > 0) {
                executeQuery(queryContent.toString(), queryID, queryParser, searcher, resultWriter);
            }
        } catch (Exception e) {
            System.out.println("Error processing the query file: " + e.getMessage());
        }
    }

    // Execute the search for a single query
    private static void executeQuery(String queryString, int queryID, MultiFieldQueryParser queryParser,
            IndexSearcher searcher, PrintWriter writer) {
        try {
            queryString = sanitizeQuery(queryString.trim()); // Clean the query string
            Query query = queryParser.parse(queryString);

            // Execute the search and get top 50 results
            ScoreDoc[] results = searcher.search(query, 50).scoreDocs;

            int rank = 1;
            for (ScoreDoc scoreDoc : results) {
                Document document = searcher.doc(scoreDoc.doc);

                String documentID = document.get("documentID");

                // Output results in the required TREC format
                writer.println(queryID + " 0 " + documentID + " " + rank + " " + scoreDoc.score + " STANDARD");
                rank++;
            }
        } catch (Exception e) {
            System.out.println("Error parsing query: " + queryString);
        }
    }

    // Select the scoring method for the searcher
    private static void applyScoringMethod(IndexSearcher searcher, int scoringOption) {
        switch (scoringOption) {
            case 0:
                searcher.setSimilarity(new ClassicSimilarity()); // TF-IDF
                break;
            case 1:
                searcher.setSimilarity(new BM25Similarity(1.5f, 0.75f)); // BM25
                break;
            case 2:
                searcher.setSimilarity(new LMDirichletSimilarity()); // LMDirichlet
                break;
            default:
                throw new IllegalArgumentException("Invalid scoring method: " + scoringOption);
        }
    }

    // Sanitize query to escape special characters
    public static String sanitizeQuery(String queryString) {
        String[] specialChars = { "\\", "+", "-", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "\"", "~", "*",
                "?", ":", "/" };
        for (String specialChar : specialChars) {
            queryString = queryString.replace(specialChar, "\\" + specialChar);
        }
        return queryString;
    }
}
