package ca.IR;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
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
            System.out.println("Usage: IndexFiles <indexDirectory> <documentDirectory>");
            return;
        }

        String indexDirectoryPath = args[0];
        String documentDirectoryPath = args[1];

        File docDirectory = new File(documentDirectoryPath);
        if (!docDirectory.exists() || !docDirectory.isDirectory()) {
            System.out.println("Invalid directory specified , specify the correct one: " + documentDirectoryPath);
            return;
        }

        Directory indexDir = FSDirectory.open(Paths.get(indexDirectoryPath));

        // tried WhitespaceAnalyzer for testing
        WhitespaceAnalyzer whitespaceAnalyzer = new WhitespaceAnalyzer();

        IndexWriterConfig iwConfig = new IndexWriterConfig(whitespaceAnalyzer);
        iwConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter idxWriter = new IndexWriter(indexDir, iwConfig)) {
            File[] listOfDocs = docDirectory.listFiles();
            if (listOfDocs != null && listOfDocs.length > 0) {
                for (File docFile : listOfDocs) {
                    if (docFile.isFile()) {
                        System.out.println("Now processing: " + docFile.getName());
                        indexDocument(idxWriter, docFile);
                    }
                }
            } else {
                System.out.println("No files found in directory: " + documentDirectoryPath);
            }
        }

        System.out.println("Indexing operation completed.");
    }

    private static void indexDocument(IndexWriter indexWriter, File documentFile) throws IOException {
        String docID = null, currentLine;
        StringBuilder docTitle = new StringBuilder();
        StringBuilder docAuthor = new StringBuilder();
        StringBuilder docContent = new StringBuilder();
        boolean capturingContent = false;

        try (BufferedReader fileReader = new BufferedReader(new FileReader(documentFile))) {
            while ((currentLine = fileReader.readLine()) != null) {
                if (currentLine.startsWith(".I")) {
                    if (docID != null) {
                        addDocumentToIndex(indexWriter, docID, docTitle.toString(), docAuthor.toString(),
                                docContent.toString());
                        resetBuilders(docTitle, docAuthor, docContent);
                    }
                    docID = currentLine.substring(3).trim();
                } else if (currentLine.startsWith(".T")) {
                    processSection(fileReader, docTitle);
                } else if (currentLine.startsWith(".A")) {
                    processSection(fileReader, docAuthor);
                } else if (currentLine.startsWith(".W")) {
                    capturingContent = true;
                } else if (capturingContent) {
                    docContent.append(currentLine).append(" ");
                }
            }

            if (docID != null) {
                addDocumentToIndex(indexWriter, docID, docTitle.toString(), docAuthor.toString(),
                        docContent.toString());
            }
        }
    }

    private static void processSection(BufferedReader fileReader, StringBuilder sectionContent) throws IOException {
        String sectionLine = fileReader.readLine();
        while (sectionLine != null && !sectionLine.startsWith(".")) {
            sectionContent.append(sectionLine).append(" ");
            sectionLine = fileReader.readLine();
        }
    }

    private static void resetBuilders(StringBuilder... builders) {
        for (StringBuilder builder : builders) {
            builder.setLength(0);
        }
    }

    private static void addDocumentToIndex(IndexWriter writer, String docID, String title, String author,
            String content) throws IOException {
        Document luceneDoc = new Document();
        luceneDoc.add(new TextField("title", title, Field.Store.YES));
        luceneDoc.add(new TextField("author", author, Field.Store.YES));
        luceneDoc.add(new TextField("contents", content, Field.Store.YES));
        luceneDoc.add(new StringField("documentID", docID, Field.Store.YES));
        writer.addDocument(luceneDoc);
    }
}