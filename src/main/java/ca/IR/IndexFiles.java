package ca.IR;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;

public class IndexFiles {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: IndexFiles <indexDir> <docsDir>");
            return;
        }

        String indexLocation = args[0];
        String documentDirectoryPath = args[1];

        File documentDirectory = new File(documentDirectoryPath);
        if (!documentDirectory.exists() || !documentDirectory.isDirectory()) {
            System.out.println("Invalid documents directory: " + documentDirectoryPath);
            return;
        }

        Directory indexDirectory = FSDirectory.open(Paths.get(indexLocation));
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
        writerConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        IndexWriter indexWriter = new IndexWriter(indexDirectory, writerConfig);

        File[] documentFiles = documentDirectory.listFiles();
        if (documentFiles != null) {
            for (File docFile : documentFiles) {
                if (docFile.isFile()) {
                    System.out.println("Indexing document: " + docFile.getName());
                    indexDocument(indexWriter, docFile);
                }
            }
        } else {
            System.out.println("No document files found in this directory: " + documentDirectoryPath);
        }

        indexWriter.close();
        System.out.println("Document indexing has been completed.");
    }

    static void indexDocument(IndexWriter indexWriter, File docFile) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(docFile))) {
            String line;
            String docID = null;
            StringBuilder title = new StringBuilder();
            StringBuilder author = new StringBuilder();
            StringBuilder content = new StringBuilder();
            boolean isContentPart = false;

            while ((line = bufferedReader.readLine()) != null) {
                if (line.startsWith(".I")) {
                    if (docID != null) {
                        addToIndex(indexWriter, docID, title.toString(), author.toString(), content.toString());
                        title.setLength(0);
                        author.setLength(0);
                        content.setLength(0);
                    }
                    docID = line.substring(3).trim();
                } else if (line.startsWith(".T")) {
                    line = bufferedReader.readLine();
                    while (line != null && !line.startsWith(".")) {
                        title.append(line).append(" ");
                        line = bufferedReader.readLine();
                    }
                } else if (line.startsWith(".A")) {
                    line = bufferedReader.readLine();
                    while (line != null && !line.startsWith(".")) {
                        author.append(line).append(" ");
                        line = bufferedReader.readLine();
                    }
                } else if (line.startsWith(".W")) {
                    isContentPart = true;
                    continue;
                }

                if (isContentPart) {
                    content.append(line).append(" ");
                }
            }

            if (docID != null) {
                addToIndex(indexWriter, docID, title.toString(), author.toString(), content.toString());
            }
        }
    }

    private static void addToIndex(IndexWriter writer, String docID, String title, String author, String content)
            throws IOException {
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("author", author, Field.Store.YES));
        doc.add(new TextField("contents", content, Field.Store.YES));
        doc.add(new TextField("documentID", docID, Field.Store.YES));

        writer.addDocument(doc);
    }
}
