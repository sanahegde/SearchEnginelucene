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

import java.io.*;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class SearchFiles {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out
                    .println("Usage: SearchFiles <indexDirectory> <queriesFilePath> <similarityType> <outputFilePath>");
            return;
        }

        String indexDirectory = args[0];
        String queriesFilePath = args[1];
        int similarityType = Integer.parseInt(args[2]);
        String outputFilePath = args[3];

        try (DirectoryReader dirReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDirectory)));
                PrintWriter resultWriter = new PrintWriter(new FileWriter(outputFilePath))) {

            IndexSearcher indexSearcher = new IndexSearcher(dirReader);
            applySimilarityModel(indexSearcher, similarityType);

            StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
            String[] queryFields = { "title", "contents" };
            Map<String, Float> fieldBoosts = new HashMap<>();
            fieldBoosts.put("title", 3.0f);
            fieldBoosts.put("contents", 1.0f);

            MultiFieldQueryParser queryParser = new MultiFieldQueryParser(queryFields, standardAnalyzer, fieldBoosts);

            parseAndProcessQueries(queriesFilePath, queryParser, indexSearcher, resultWriter);
        }

        System.out.println("Search process completed. Results saved in: " + outputFilePath);
    }

    private static void parseAndProcessQueries(String queryFilePath, MultiFieldQueryParser queryParser,
            IndexSearcher indexSearcher,
            PrintWriter resultWriter) {
        File queryFile = new File(queryFilePath);

        if (!queryFile.exists() || !queryFile.canRead()) {
            System.out.println("Cannot access query file: " + queryFilePath);
            return;
        }

        try (Scanner queryScanner = new Scanner(queryFile)) {
            int queryId = 1;
            StringBuilder currentQuery = new StringBuilder();
            boolean queryReading = false;

            while (queryScanner.hasNextLine()) {
                String currentLine = queryScanner.nextLine().trim();

                if (currentLine.startsWith(".I")) {
                    if (queryReading) {
                        executeQuery(currentQuery.toString(), queryId, queryParser, indexSearcher, resultWriter);
                        currentQuery.setLength(0);
                    }
                    queryId++;
                    queryReading = false;
                } else if (currentLine.startsWith(".W")) {
                    queryReading = true;
                } else if (queryReading) {
                    currentQuery.append(currentLine).append(" ");
                }
            }

            if (currentQuery.length() > 0) {
                executeQuery(currentQuery.toString(), queryId, queryParser, indexSearcher, resultWriter);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Error: Could not read the query file - " + e.getMessage());
        }
    }

    private static void executeQuery(String queryText, int queryId, MultiFieldQueryParser queryParser,
            IndexSearcher indexSearcher,
            PrintWriter resultWriter) {
        try {
            queryText = sanitizeQuery(queryText.trim());
            Query parsedQuery = queryParser.parse(queryText);

            ScoreDoc[] searchResults = indexSearcher.search(parsedQuery, 50).scoreDocs;

            int rank = 1;
            for (ScoreDoc scoreDoc : searchResults) {
                Document retrievedDoc = indexSearcher.doc(scoreDoc.doc);
                String docID = retrievedDoc.get("documentID");

                resultWriter.println(queryId + " 0 " + docID + " " + rank + " " + scoreDoc.score + " STANDARD");
                rank++;
            }
        } catch (Exception e) {
            System.out.println("Error while parsing query: " + queryText);
        }
    }

    private static void applySimilarityModel(IndexSearcher searcher, int similarityType) {
        switch (similarityType) {
            case 0:
                searcher.setSimilarity(new ClassicSimilarity());
                break;
            case 1:
                searcher.setSimilarity(new BM25Similarity(1.5f, 0.75f));
                break;
            case 2:
                searcher.setSimilarity(new LMDirichletSimilarity());
                break;
            default:
                throw new IllegalArgumentException("Invalid similarity type: " + similarityType);
        }
    }

    private static String sanitizeQuery(String rawQuery) {
        String[] specialCharacters = { "\\", "+", "-", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "\"", "~",
                "*", "?", ":", "/" };
        for (String specialChar : specialCharacters) {
            rawQuery = rawQuery.replace(specialChar, "\\" + specialChar);
        }
        return rawQuery;
    }
}
