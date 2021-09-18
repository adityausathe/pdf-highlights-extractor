package com.adus.pdf_highlights_extractor;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.lang.String.format;

public class HighLightsExtractor {
    private final File inputPdfFile;
    private final File outputPdfFile;

    public HighLightsExtractor(File inputPdfFile, File outputPdfFile) {
        this.inputPdfFile = inputPdfFile;
        this.outputPdfFile = outputPdfFile;
    }

    public void process() {
        try (PDDocument inputPdfDocObj = PDDocument.load(inputPdfFile); PDDocument outputPdfDocObj = new PDDocument()) {
            int currentInPageNumber = 0;
            for (PDPage inputPdfPage : inputPdfDocObj.getDocumentCatalog().getPages()) {
                ++currentInPageNumber;
                List<PDAnnotation> pageAnnotations = inputPdfPage.getAnnotations();
                int pageHeight = 0;
                int pageWidth = 0;
                for (PDAnnotation annotation : pageAnnotations) {
                    if (annotation instanceof PDAnnotationTextMarkup) {
                        pageHeight += annotation.getRectangle().getHeight() + 5;
                        if (pageWidth < annotation.getRectangle().getWidth())
                            pageWidth = (int) annotation.getRectangle().getWidth();
                    }
                }
                // skip any other type of annotation
                if (pageAnnotations.stream().noneMatch(a -> a instanceof PDAnnotationTextMarkup)) {
                    continue;
                }

                PDPageContentStream contentStream = createNewPageToHoldHighlightSnaps(outputPdfDocObj, currentInPageNumber, pageHeight, pageWidth);

                float snapSaveAtYLoc = pageHeight + 20;
                for (PDAnnotation annotation : pageAnnotations) {
                    if (annotation instanceof PDAnnotationTextMarkup) {
                        snapSaveAtYLoc -= annotation.getRectangle().getHeight() + 5;
                        cropHighlightAndSaveSnap((PDAnnotationTextMarkup) annotation, inputPdfDocObj, inputPdfPage, currentInPageNumber, outputPdfDocObj, contentStream, snapSaveAtYLoc);
                    }

                }
                contentStream.close();
            }
            outputPdfDocObj.save(outputPdfFile.getPath());
        } catch (IOException e) {
            System.err.println("Something terribly went wrong!!");
            e.printStackTrace();
        }
    }

    // create a new page in output pdf file, where these annotations will be pasted.
    // setup basic properties for the page.
    private PDPageContentStream createNewPageToHoldHighlightSnaps(PDDocument outputPdfDocObj, int currentPageNumber, int pageHeight, int pageWidth) throws IOException {
        outputPdfDocObj.addPage(new PDPage(new PDRectangle(pageWidth + 1, pageHeight + 20)));
        //outputPdfDocObj.addPage(new PDPage(new PDRectangle(highlight.getRectangle().getWidth()*10, highlight.getRectangle().getHeight()*10)));
        PDPage outPDFDocCurrentPage = outputPdfDocObj.getPage(outputPdfDocObj.getDocumentCatalog().getPages().getCount() - 1);
        PDPageContentStream contentStream = new PDPageContentStream(outputPdfDocObj, outPDFDocCurrentPage, AppendMode.APPEND, true);

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 11);
        contentStream.newLineAtOffset(0, 5);
        // add context to the page, for tracking where this highlight is coming from.
        contentStream.showText(format("Page: %d", currentPageNumber));
        contentStream.endText();
        return contentStream;
    }

    private void cropHighlightAndSaveSnap(PDAnnotationTextMarkup highlight,
                                          PDDocument inputPdfDocObj, PDPage inputPdfPage, int currentInPageNumber,
                                          PDDocument outputPdfDocObj, PDPageContentStream contentStream, float snapSaveAtYLoc) throws IOException {
        float boxHeight = highlight.getRectangle().getHeight();
        float boxLowerLeftX = highlight.getRectangle().getLowerLeftX();

        // We get an array of small-sized highlight-boxes; likely one box per line of a highlight in the original text.
        // We need to crop all those boxes and then stitch them together so that in the end the final snapshot should
        // look exactly like the one from the source-pdf file.
        COSArray quadsArray = (COSArray) highlight.getCOSObject().getDictionaryObject(COSName.getPDFName("QuadPoints"));
        for (int miniatureBoxIdx = 1, offSetIntoQuadPoints = 0; miniatureBoxIdx <= (quadsArray.size() / 8); miniatureBoxIdx++, offSetIntoQuadPoints += 8) {
            // locate highlight's rectangular boundaries
            float llx = ((COSNumber) quadsArray.get(4 + offSetIntoQuadPoints)).floatValue(); // lower left x
            float lly = ((COSNumber) quadsArray.get(5 + offSetIntoQuadPoints)).floatValue(); // lower left y
            float urx = ((COSNumber) quadsArray.get(2 + offSetIntoQuadPoints)).floatValue(); // upper right x
            float ury = ((COSNumber) quadsArray.get(3 + offSetIntoQuadPoints)).floatValue(); // upper right y

            // crop the miniature highlight snap
            PDRectangle highlightCropBox = new PDRectangle();
            highlightCropBox.setLowerLeftX(llx);
            highlightCropBox.setLowerLeftY(lly);
            highlightCropBox.setUpperRightX(urx);
            highlightCropBox.setUpperRightY(ury);
            inputPdfPage.setCropBox(highlightCropBox);

            BufferedImage croppedImage = new PDFRenderer(inputPdfDocObj).renderImageWithDPI(currentInPageNumber - 1, 300);
            PDImageXObject croppedPdfImage = JPEGFactory.createFromImage(outputPdfDocObj, croppedImage);
            boxHeight = boxHeight - highlightCropBox.getHeight();

            // save cropped highlight-snap to output pdf. Also consider where this miniature snap should be placed.
            float snapSaveAtXLoc = highlightCropBox.getLowerLeftX() - boxLowerLeftX;
            contentStream.drawImage(croppedPdfImage, snapSaveAtXLoc, snapSaveAtYLoc + boxHeight, highlightCropBox.getWidth(), highlightCropBox.getHeight());
        }
        contentStream.moveTo(0, snapSaveAtYLoc - 3);
        contentStream.lineTo(30, snapSaveAtYLoc - 3);
        contentStream.setStrokingColor(Color.red);
        contentStream.stroke();
    }
}
