// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "TypesetService.h"
#import "TypesetTemplates.h"

#import "minizip/unzip.h"
#import "minizip/zip.h"
#import <zlib.h>

#include <Poco/AutoPtr.h>
#include <Poco/DOM/DOMParser.h>
#include <Poco/DOM/DOMWriter.h>
#include <Poco/DOM/Document.h>
#include <Poco/DOM/Element.h>
#include <Poco/DOM/Node.h>
#include <Poco/DOM/NodeList.h>
#include <Poco/DOM/Text.h>
#include <Poco/Exception.h>
#include <Poco/XML/XMLWriter.h>

#include <sstream>
#include <string>
#include <vector>

using Poco::AutoPtr;
using Poco::XML::DOMParser;
using Poco::XML::DOMWriter;
using Poco::XML::Document;
using Poco::XML::Element;
using Poco::XML::Node;
using Poco::XML::NodeList;
using Poco::XML::XMLWriter;

static NSString * const kTypesetWordNS = @"http://schemas.openxmlformats.org/wordprocessingml/2006/main";
static NSString * const kTypesetDrawingNS = @"http://schemas.openxmlformats.org/drawingml/2006/main";
static NSString * const kTypesetWpNS = @"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
static NSString * const kTypesetRelNS = @"http://schemas.openxmlformats.org/package/2006/relationships";
static NSString * const kTypesetRelOfficeNS = @"http://schemas.openxmlformats.org/officeDocument/2006/relationships";
static NSString * const kTypesetPicNS = @"http://schemas.openxmlformats.org/drawingml/2006/picture";
static NSString * const kTypesetContentTypesNS = @"http://schemas.openxmlformats.org/package/2006/content-types";
static const long kTypesetDefaultEmu = 5L * 914400L;

static std::string TypesetUTF8(NSString *value) {
    return value ? std::string(value.UTF8String ?: "") : std::string();
}

static NSString *TypesetNSString(const std::string& value) {
    return [[NSString alloc] initWithBytes:value.data()
                                   length:value.size()
                                 encoding:NSUTF8StringEncoding] ?: @"";
}

static AutoPtr<Document> TypesetParseXml(NSData *data) {
    if (!data) return nullptr;
    try {
        DOMParser parser;
        return AutoPtr<Document>(parser.parseMemory(static_cast<const char *>(data.bytes), data.length));
    } catch (const Poco::Exception&) {
        return nullptr;
    } catch (const std::exception&) {
        return nullptr;
    }
}

static AutoPtr<Document> TypesetParseXmlString(NSString *xml) {
    NSData *data = [xml dataUsingEncoding:NSUTF8StringEncoding];
    return TypesetParseXml(data);
}

static NSData *TypesetSerializeXml(Document *document) {
    if (!document) return nil;
    try {
        std::ostringstream stream;
        DOMWriter writer;
        writer.setOptions(XMLWriter::PRETTY_PRINT);
        writer.writeNode(stream, document);
        const std::string xml = stream.str();
        return [NSData dataWithBytes:xml.data() length:xml.size()];
    } catch (const Poco::Exception&) {
        return nil;
    } catch (const std::exception&) {
        return nil;
    }
}

static std::vector<Element *> TypesetElements(Node *node, NSString *localName, NSString *namespaceURI) {
    std::vector<Element *> elements;
    if (!node) return elements;
    AutoPtr<NodeList> list;
    if (node->nodeType() == Node::DOCUMENT_NODE) {
        list = static_cast<Document *>(node)->getElementsByTagNameNS(TypesetUTF8(namespaceURI),
                                                                     TypesetUTF8(localName));
    } else if (node->nodeType() == Node::ELEMENT_NODE) {
        list = static_cast<Element *>(node)->getElementsByTagNameNS(TypesetUTF8(namespaceURI),
                                                                    TypesetUTF8(localName));
    }
    if (!list) return elements;
    elements.reserve(list->length());
    for (unsigned long i = 0; i < list->length(); ++i) {
        Node *item = list->item(i);
        if (item && item->nodeType() == Node::ELEMENT_NODE) {
            elements.push_back(static_cast<Element *>(item));
        }
    }
    return elements;
}

static NSString *TypesetLocalName(Node *node) {
    return node ? TypesetNSString(node->localName()) : nil;
}

static NSString *TypesetStringValue(Node *node) {
    return node ? TypesetNSString(node->innerText()) : nil;
}

static void TypesetSetStringValue(Element *element, NSString *value) {
    if (!element) return;
    while (Node *child = element->firstChild()) {
        element->removeChild(child);
    }
    Document *document = element->ownerDocument();
    element->appendChild(document->createTextNode(TypesetUTF8(value ?: @"")));
}

static NSString *TypesetAttribute(Element *element, NSString *name) {
    return element ? TypesetNSString(element->getAttribute(TypesetUTF8(name))) : nil;
}

static NSString *TypesetAttributeNS(Element *element, NSString *localName, NSString *namespaceURI) {
    return element ? TypesetNSString(element->getAttributeNS(TypesetUTF8(namespaceURI),
                                                              TypesetUTF8(localName))) : nil;
}

static Element *TypesetCreateElement(Document *document, NSString *qualifiedName, NSString *namespaceURI) {
    return document ? document->createElementNS(TypesetUTF8(namespaceURI), TypesetUTF8(qualifiedName)) : nullptr;
}

static void TypesetSetAttribute(Element *element, NSString *qualifiedName, NSString *value) {
    if (element) element->setAttribute(TypesetUTF8(qualifiedName), TypesetUTF8(value));
}

static void TypesetSetAttributeNS(Element *element,
                                  NSString *qualifiedName,
                                  NSString *namespaceURI,
                                  NSString *value) {
    if (element) {
        element->setAttributeNS(TypesetUTF8(namespaceURI), TypesetUTF8(qualifiedName), TypesetUTF8(value));
    }
}

static NSUInteger TypesetIndexOfChild(Node *parent, Node *child) {
    NSUInteger index = 0;
    for (Node *current = parent ? parent->firstChild() : nullptr;
         current;
         current = current->nextSibling(), ++index) {
        if (current == child) return index;
    }
    return NSNotFound;
}

static void TypesetInsertChild(Node *parent, Node *child, NSUInteger index) {
    if (!parent || !child) return;
    Node *reference = parent->firstChild();
    for (NSUInteger i = 0; reference && i < index; ++i) {
        reference = reference->nextSibling();
    }
    parent->insertBefore(child, reference);
}

static NSDictionary<NSString *, NSArray<NSDictionary *> *> *TypesetPlaceholderMap(void) {
    static NSDictionary *map = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        map = @{
            @"paper": @[
                @{@"placeholder": @"论文标题", @"key": @"title"},
                @{@"placeholder": @"摘要正文", @"key": @"abstract"},
                @{@"placeholder": @"关键词", @"key": @"keywords"},
                @{@"placeholder": @"引言正文", @"key": @"introduction"},
                @{@"placeholder": @"一级标题", @"key": @"heading1"},
                @{@"placeholder": @"二级标题", @"key": @"heading2"},
                @{@"placeholder": @"三级标题", @"key": @"heading3"},
                @{@"placeholder": @"正文", @"key": @"body"},
                @{@"placeholder": @"结语正文", @"key": @"conclusion_body"},
                @{@"placeholder": @"致谢内容", @"key": @"ack_body"},
            ],
            @"gov": @[
                @{@"placeholder": @"主送机关：", @"key": @"recipient"},
                @{@"placeholder": @"××", @"key": @"body"},
                @{@"placeholder": @"发文机关署名（比日期长）", @"key": @"signature_org"},
                @{@"placeholder": @"20××年×月×日", @"key": @"signature_date"},
                @{@"placeholder": @"（附注内容）", @"key": @"notes"},
            ],
            @"contract": @[
                @{@"placeholder": @"合同协议", @"key": @"title"},
                @{@"placeholder": @"合同编号：", @"key": @"contract_number"},
                @{@"placeholder": @"甲方：", @"key": @"party_a"},
                @{@"placeholder": @"身份证号码：", @"key": @"party_a_id"},
                @{@"placeholder": @"乙方：", @"key": @"party_b"},
                @{@"placeholder": @"按照平等互利", @"key": @"preamble"},
                @{@"placeholder": @"第一条 一级标题", @"key": @"clause_title"},
                @{@"placeholder": @"1.1 二级标题", @"key": @"clause_subtitle"},
                @{@"placeholder": @"正文", @"key": @"clause_body"},
            ],
            @"general": @[
                @{@"placeholder": @"文档标题", @"key": @"title"},
                @{@"placeholder": @"一级标题", @"key": @"heading1"},
                @{@"placeholder": @"二级标题", @"key": @"heading2"},
                @{@"placeholder": @"三级标题", @"key": @"heading3"},
                @{@"placeholder": @"正文段落", @"key": @"body"},
            ],
        };
    });
    return map;
}

static NSString *TypesetTemplateBase64(NSString *typesetType) {
    if ([typesetType isEqualToString:@"paper"]) return TypesetTemplateBase64_paper;
    if ([typesetType isEqualToString:@"gov"]) return TypesetTemplateBase64_gov;
    if ([typesetType isEqualToString:@"contract"]) return TypesetTemplateBase64_contract;
    return TypesetTemplateBase64_general;
}

static NSString *TypesetMatchPlaceholder(NSString *text, NSArray<NSDictionary *> *entries, NSMutableSet<NSString *> *usedKeys) {
    NSString *trimmed = [text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (trimmed.length == 0) return nil;
    for (NSDictionary *entry in entries) {
        NSString *placeholder = entry[@"placeholder"];
        NSString *key = entry[@"key"];
        if ([trimmed isEqualToString:placeholder]
            || (placeholder.length >= 2 && [trimmed hasPrefix:placeholder])) {
            if ([key isEqualToString:@"party_a_id"] && [usedKeys containsObject:key]) {
                key = @"party_b_id";
            }
            return key;
        }
    }
    return nil;
}

static NSString *TypesetParagraphText(Element *paragraph) {
    NSMutableString *text = [NSMutableString string];
    for (Element *node : TypesetElements(paragraph, @"t", kTypesetWordNS)) {
        NSString *value = TypesetStringValue(node);
        if (value.length > 0) {
            [text appendString:value];
        }
    }
    return [[text copy] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

static void TypesetReplaceParagraphText(Element *paragraph, NSString *newText) {
    std::vector<Element *> runs = TypesetElements(paragraph, @"r", kTypesetWordNS);
    Element *templateRun = runs.empty() ? nullptr : runs.front();
    Element *templateRPr = nullptr;
    if (templateRun) {
        std::vector<Element *> rPrs = TypesetElements(templateRun, @"rPr", kTypesetWordNS);
        templateRPr = rPrs.empty() ? nullptr : rPrs.front();
    }
    for (Element *run : runs) {
        if (run->parentNode() == paragraph) paragraph->removeChild(run);
    }
    Document *document = paragraph->ownerDocument();
    Element *newRun = TypesetCreateElement(document, @"w:r", kTypesetWordNS);
    if (templateRPr) {
        newRun->appendChild(templateRPr->cloneNode(true));
    }
    Element *textNode = TypesetCreateElement(document, @"w:t", kTypesetWordNS);
    TypesetSetAttributeNS(textNode, @"xml:space", @"http://www.w3.org/XML/1998/namespace", @"preserve");
    TypesetSetStringValue(textNode, newText ?: @"");
    newRun->appendChild(textNode);
    paragraph->appendChild(newRun);
}

static Element *TypesetCloneParagraph(Element *source, NSString *text) {
    Element *newP = TypesetCreateElement(source->ownerDocument(), @"w:p", kTypesetWordNS);
    std::vector<Element *> pPrs = TypesetElements(source, @"pPr", kTypesetWordNS);
    if (!pPrs.empty()) {
        newP->appendChild(pPrs.front()->cloneNode(true));
    }
    TypesetReplaceParagraphText(newP, text);
    return newP;
}

static BOOL TypesetFillDocumentXml(NSData *documentXml,
                                   NSString *typesetType,
                                   NSDictionary<NSString *, NSString *> *sections,
                                   NSData **outData) {
    AutoPtr<Document> doc = TypesetParseXml(documentXml);
    if (!doc) return NO;
    std::vector<Element *> bodies = TypesetElements(doc, @"body", kTypesetWordNS);
    Element *body = bodies.empty() ? nullptr : bodies.front();
    if (!body) return NO;
    NSArray<NSDictionary *> *entries = TypesetPlaceholderMap()[typesetType] ?: TypesetPlaceholderMap()[@"general"];
    NSMutableSet<NSString *> *usedKeys = [NSMutableSet set];
    std::vector<Element *> paragraphs;
    for (Node *child = body->firstChild(); child; child = child->nextSibling()) {
        if (child->nodeType() == Node::ELEMENT_NODE && [TypesetLocalName(child) isEqualToString:@"p"]) {
            paragraphs.push_back(static_cast<Element *>(child));
        }
    }
    for (Element *paragraph : paragraphs) {
        NSString *paraText = TypesetParagraphText(paragraph);
        if (paraText.length == 0) continue;
        NSString *sectionKey = TypesetMatchPlaceholder(paraText, entries, usedKeys);
        if (!sectionKey) continue;
        [usedKeys addObject:sectionKey];
        NSString *content = sections[sectionKey];
        if (content.length == 0) continue;
        NSArray<NSString *> *parts = [content componentsSeparatedByString:@"\n\n"];
        TypesetReplaceParagraphText(paragraph, parts.firstObject ?: @"");
        Element *anchor = paragraph;
        for (NSUInteger i = 1; i < parts.count; i++) {
            NSString *segment = [parts[i] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (segment.length == 0) continue;
            Element *newP = TypesetCloneParagraph(paragraph, segment);
            TypesetInsertChild(body, newP, TypesetIndexOfChild(body, anchor) + 1);
            anchor = newP;
        }
    }
    NSData *xmlData = TypesetSerializeXml(doc);
    if (!xmlData) return NO;
    *outData = xmlData;
    return YES;
}

static BOOL TypesetUnzipEntries(NSData *zipData, NSMutableDictionary<NSString *, NSData *> *entries) {
    NSString *tempPath = [NSTemporaryDirectory() stringByAppendingPathComponent:
        [[NSUUID UUID] UUIDString]];
    if (![zipData writeToFile:tempPath atomically:YES]) return NO;
    unzFile zip = unzOpen(tempPath.UTF8String);
    if (!zip) {
        [[NSFileManager defaultManager] removeItemAtPath:tempPath error:nil];
        return NO;
    }
    if (unzGoToFirstFile(zip) != UNZ_OK) {
        unzClose(zip);
        [[NSFileManager defaultManager] removeItemAtPath:tempPath error:nil];
        return NO;
    }
    do {
        unz_file_info fileInfo = {};
        char filename[512] = {0};
        if (unzGetCurrentFileInfo(zip, &fileInfo, filename, sizeof(filename), NULL, 0, NULL, 0) != UNZ_OK) {
            continue;
        }
        if (unzOpenCurrentFile(zip) != UNZ_OK) continue;
        NSMutableData *data = [NSMutableData dataWithLength:fileInfo.uncompressed_size];
        int read = unzReadCurrentFile(zip, data.mutableBytes, (unsigned)fileInfo.uncompressed_size);
        unzCloseCurrentFile(zip);
        if (read >= 0) {
            [data setLength:(NSUInteger)read];
            entries[@(filename)] = data;
        }
    } while (unzGoToNextFile(zip) == UNZ_OK);
    unzClose(zip);
    [[NSFileManager defaultManager] removeItemAtPath:tempPath error:nil];
    return entries.count > 0;
}

static BOOL TypesetWriteZip(NSDictionary<NSString *, NSData *> *entries, NSURL *outputURL) {
    zipFile zip = zipOpen(outputURL.path.UTF8String, APPEND_STATUS_CREATE);
    if (!zip) return NO;
    for (NSString *name in entries) {
        NSData *data = entries[name];
        zip_fileinfo info = {};
        if (zipOpenNewFileInZip(zip, name.UTF8String, &info, NULL, 0, NULL, 0, NULL, Z_DEFLATED, Z_DEFAULT_COMPRESSION) != ZIP_OK) {
            zipClose(zip, NULL);
            return NO;
        }
        if (zipWriteInFileInZip(zip, data.bytes, (unsigned)data.length) != ZIP_OK) {
            zipCloseFileInZip(zip);
            zipClose(zip, NULL);
            return NO;
        }
        zipCloseFileInZip(zip);
    }
    zipClose(zip, NULL);
    return YES;
}

static NSDictionary<NSString *, NSString *> *TypesetBuildImageRelMap(NSData *relsXml) {
    if (!relsXml) return @{};
    AutoPtr<Document> relsDoc = TypesetParseXml(relsXml);
    if (!relsDoc) return @{};
    NSMutableDictionary<NSString *, NSString *> *relMap = [NSMutableDictionary dictionary];
    for (Element *rel : TypesetElements(relsDoc->documentElement(), @"Relationship", kTypesetRelNS)) {
        NSString *type = TypesetAttribute(rel, @"Type");
        NSString *target = TypesetAttribute(rel, @"Target");
        NSString *relId = TypesetAttribute(rel, @"Id");
        if (relId.length > 0 && target.length > 0 && [type containsString:@"image"]) {
            relMap[relId] = target;
        }
    }
    return relMap;
}

static NSString *TypesetMimeForExtension(NSString *ext) {
    NSString *lower = ext.lowercaseString;
    if ([lower isEqualToString:@"png"]) return @"image/png";
    if ([lower isEqualToString:@"jpg"] || [lower isEqualToString:@"jpeg"]) return @"image/jpeg";
    if ([lower isEqualToString:@"gif"]) return @"image/gif";
    if ([lower isEqualToString:@"bmp"]) return @"image/bmp";
    return @"application/octet-stream";
}

static void TypesetCollectParagraphContent(Element *paragraph,
                                           NSDictionary<NSString *, NSString *> *relMap,
                                           NSDictionary<NSString *, NSData *> *entries,
                                           NSMutableString *paraText,
                                           NSMutableDictionary<NSString *, NSDictionary *> *images,
                                           int *imgCounter) {
    for (Element *run : TypesetElements(paragraph, @"r", kTypesetWordNS)) {
        for (Element *textNode : TypesetElements(run, @"t", kTypesetWordNS)) {
            NSString *value = TypesetStringValue(textNode);
            if (value.length > 0) {
                [paraText appendString:value];
            }
        }
        for (Element *drawing : TypesetElements(run, @"drawing", kTypesetWordNS)) {
            for (Element *blip : TypesetElements(drawing, @"blip", kTypesetDrawingNS)) {
                NSString *embed = TypesetAttributeNS(blip, @"embed", kTypesetRelOfficeNS);
                if (embed.length == 0) continue;
                NSString *target = relMap[embed];
                if (target.length == 0) continue;
                NSString *mediaPath = [target hasPrefix:@"word/"] ? target : [@"word/" stringByAppendingString:target];
                NSData *imageData = entries[mediaPath];
                if (!imageData) continue;
                (*imgCounter)++;
                NSString *marker = [NSString stringWithFormat:@"图%d", *imgCounter];
                [paraText appendFormat:@"[%@]", marker];
                NSString *ext = mediaPath.pathExtension.length > 0 ? mediaPath.pathExtension : @"png";
                images[marker] = @{
                    @"base64": [imageData base64EncodedStringWithOptions:0],
                    @"mimeType": TypesetMimeForExtension(ext),
                    @"extension": ext,
                    @"cx": @(kTypesetDefaultEmu),
                    @"cy": @(kTypesetDefaultEmu),
                };
            }
        }
    }
}

static NSDictionary *TypesetExtractStructuredFromEntries(NSDictionary<NSString *, NSData *> *entries) {
    NSData *documentXml = entries[@"word/document.xml"];
    if (!documentXml) return nil;
    AutoPtr<Document> doc = TypesetParseXml(documentXml);
    if (!doc) return nil;
    std::vector<Element *> bodies = TypesetElements(doc, @"body", kTypesetWordNS);
    Element *body = bodies.empty() ? nullptr : bodies.front();
    if (!body) return nil;
    NSDictionary<NSString *, NSString *> *relMap =
        TypesetBuildImageRelMap(entries[@"word/_rels/document.xml.rels"]);
    NSMutableArray<NSString *> *paragraphs = [NSMutableArray array];
    NSMutableString *fullText = [NSMutableString string];
    NSMutableDictionary<NSString *, NSDictionary *> *images = [NSMutableDictionary dictionary];
    int imgCounter = 0;
    for (Node *child = body->firstChild(); child; child = child->nextSibling()) {
        if (child->nodeType() != Node::ELEMENT_NODE || ![TypesetLocalName(child) isEqualToString:@"p"]) continue;
        NSMutableString *paraText = [NSMutableString string];
        TypesetCollectParagraphContent(static_cast<Element *>(child), relMap, entries,
                                       paraText, images, &imgCounter);
        NSString *para = [[paraText copy] stringByTrimmingCharactersInSet:
            [NSCharacterSet whitespaceAndNewlineCharacterSet]];
        if (para.length == 0) continue;
        [paragraphs addObject:para];
        if (fullText.length > 0) [fullText appendString:@"\n\n"];
        [fullText appendString:para];
    }
    if (paragraphs.count == 0) return nil;
    NSMutableDictionary *result = [@{
        @"fullText": fullText,
        @"paragraphs": paragraphs,
    } mutableCopy];
    if (images.count > 0) {
        result[@"images"] = images;
    }
    return result;
}

static NSArray<NSString *> *TypesetCollectDrawingText(NSData *slideXml) {
    NSMutableArray<NSString *> *parts = [NSMutableArray array];
    AutoPtr<Document> doc = TypesetParseXml(slideXml);
    if (doc) {
        for (Element *node : TypesetElements(doc, @"t", kTypesetDrawingNS)) {
            NSString *value = [TypesetStringValue(node) stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (value.length > 0) {
                [parts addObject:value];
            }
        }
        if (parts.count > 0) {
            return parts;
        }
    }
    NSString *xml = [[NSString alloc] initWithData:slideXml encoding:NSUTF8StringEncoding] ?: @"";
    NSRegularExpression *re = [NSRegularExpression
        regularExpressionWithPattern:@"<a:t(?:\\s[^>]*)?>([^<]*)</a:t>"
                             options:NSRegularExpressionCaseInsensitive
                               error:nil];
    NSArray<NSTextCheckingResult *> *matches =
        [re matchesInString:xml options:0 range:NSMakeRange(0, xml.length)];
    for (NSTextCheckingResult *match in matches) {
        if (match.numberOfRanges < 2) {
            continue;
        }
        NSString *value = [[xml substringWithRange:[match rangeAtIndex:1]]
            stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        value = [value stringByReplacingOccurrencesOfString:@"&amp;" withString:@"&"];
        value = [value stringByReplacingOccurrencesOfString:@"&lt;" withString:@"<"];
        value = [value stringByReplacingOccurrencesOfString:@"&gt;" withString:@">"];
        value = [value stringByReplacingOccurrencesOfString:@"&quot;" withString:@"\""];
        value = [value stringByReplacingOccurrencesOfString:@"&apos;" withString:@"'"];
        if (value.length > 0) {
            [parts addObject:value];
        }
    }
    if (parts.count == 0 && xml.length > 0) {
        NSString *stripped = [xml stringByReplacingOccurrencesOfString:@"<[^>]+>"
                                                            withString:@" "
                                                               options:NSRegularExpressionSearch
                                                                 range:NSMakeRange(0, xml.length)];
        stripped = [stripped stringByReplacingOccurrencesOfString:@"\\s+"
                                                       withString:@" "
                                                          options:NSRegularExpressionSearch
                                                            range:NSMakeRange(0, stripped.length)];
        stripped = [stripped stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        if (stripped.length > 0) {
            [parts addObject:stripped];
        }
    }
    return parts;
}

static NSDictionary *TypesetExtractFromPptxEntries(NSDictionary<NSString *, NSData *> *entries) {
    NSMutableArray<NSNumber *> *indices = [NSMutableArray array];
    for (NSString *key in entries) {
        if (![key hasPrefix:@"ppt/slides/slide"] || ![key hasSuffix:@".xml"] || [key containsString:@"_rels"]) {
            continue;
        }
        NSString *num = [key substringWithRange:NSMakeRange(@"ppt/slides/slide".length,
            key.length - @"ppt/slides/slide".length - 4)];
        NSInteger idx = num.integerValue;
        if (idx > 0) {
            [indices addObject:@(idx)];
        }
    }
    [indices sortUsingSelector:@selector(compare:)];
    NSMutableArray<NSString *> *paragraphs = [NSMutableArray array];
    NSMutableString *fullText = [NSMutableString string];
    for (NSNumber *idx in indices) {
        NSString *name = [NSString stringWithFormat:@"ppt/slides/slide%@.xml", idx];
        NSData *slideXml = entries[name];
        if (!slideXml) {
            continue;
        }
        NSArray<NSString *> *parts = TypesetCollectDrawingText(slideXml);
        if (parts.count == 0) {
            continue;
        }
        NSString *slideText = [parts componentsJoinedByString:@"\n"];
        [paragraphs addObject:slideText];
        if (fullText.length > 0) {
            [fullText appendString:@"\n\n"];
        }
        [fullText appendFormat:@"【幻灯片 %@】\n%@", idx, slideText];
    }
    if (paragraphs.count == 0) {
        return nil;
    }
    return @{
        @"fullText": fullText,
        @"paragraphs": paragraphs,
    };
}

static NSDictionary *TypesetExtractFromOdfEntries(NSDictionary<NSString *, NSData *> *entries) {
    NSData *contentXml = entries[@"content.xml"];
    if (!contentXml) {
        return nil;
    }
    AutoPtr<Document> doc = TypesetParseXml(contentXml);
    NSMutableArray<NSString *> *paragraphs = [NSMutableArray array];
    if (doc) {
        static NSString * const kTypesetOdfTextNS = @"urn:oasis:names:tc:opendocument:xmlns:text:1.0";
        std::vector<Element *> nodes = TypesetElements(doc, @"p", kTypesetOdfTextNS);
        std::vector<Element *> headings = TypesetElements(doc, @"h", kTypesetOdfTextNS);
        nodes.insert(nodes.end(), headings.begin(), headings.end());
        for (Element *node : nodes) {
            NSString *value = [TypesetStringValue(node) stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (value.length > 0) {
                [paragraphs addObject:value];
            }
        }
    }
    if (paragraphs.count == 0) {
        NSString *xml = [[NSString alloc] initWithData:contentXml encoding:NSUTF8StringEncoding] ?: @"";
        NSRegularExpression *re = [NSRegularExpression
            regularExpressionWithPattern:@"<text:(?:p|h)(?:\\s[^>]*)?>([\\s\\S]*?)</text:(?:p|h)>"
                                 options:NSRegularExpressionCaseInsensitive
                                   error:nil];
        NSArray<NSTextCheckingResult *> *matches =
            [re matchesInString:xml options:0 range:NSMakeRange(0, xml.length)];
        for (NSTextCheckingResult *match in matches) {
            if (match.numberOfRanges < 2) {
                continue;
            }
            NSString *inner = [xml substringWithRange:[match rangeAtIndex:1]];
            inner = [inner stringByReplacingOccurrencesOfString:@"<[^>]+>" withString:@""
                                                        options:NSRegularExpressionSearch
                                                          range:NSMakeRange(0, inner.length)];
            inner = [inner stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (inner.length > 0) {
                [paragraphs addObject:inner];
            }
        }
    }
    if (paragraphs.count == 0) {
        return nil;
    }
    return @{
        @"fullText": [paragraphs componentsJoinedByString:@"\n\n"],
        @"paragraphs": paragraphs,
    };
}

@implementation TypesetService

+ (NSDictionary *)extractStructuredFromFile:(NSURL *)fileURL {
    if (!fileURL || ![[NSFileManager defaultManager] fileExistsAtPath:fileURL.path]) {
        return nil;
    }
    NSData *data = [NSData dataWithContentsOfURL:fileURL];
    if (!data) return nil;
    NSMutableDictionary<NSString *, NSData *> *entries = [NSMutableDictionary dictionary];
    if (!TypesetUnzipEntries(data, entries)) {
        NSLog(@"[AIExtract] unzip failed path=%@", fileURL.path);
        return nil;
    }
    if (entries[@"word/document.xml"]) {
        NSLog(@"[AIExtract] zip format=docx entries=%lu", (unsigned long)entries.count);
        return TypesetExtractStructuredFromEntries(entries);
    }
    NSDictionary *pptx = TypesetExtractFromPptxEntries(entries);
    if (pptx) {
        NSLog(@"[AIExtract] zip format=pptx entries=%lu", (unsigned long)entries.count);
        return pptx;
    }
    NSDictionary *odf = TypesetExtractFromOdfEntries(entries);
    if (odf) {
        NSLog(@"[AIExtract] zip format=odf entries=%lu", (unsigned long)entries.count);
        return odf;
    }
    NSLog(@"[AIExtract] zip format=unknown entries=%lu keys=%@",
          (unsigned long)entries.count,
          [[entries allKeys] componentsJoinedByString:@","]);
    return nil;
}

+ (NSURL *)fillTemplateWithType:(NSString *)typesetType
                       sections:(NSDictionary<NSString *,NSString *> *)sections
                     sourceName:(NSString *)sourceName {
    NSString *base64 = TypesetTemplateBase64(typesetType ?: @"general");
    NSData *templateData = [[NSData alloc] initWithBase64EncodedString:base64 options:0];
    if (!templateData || sections.count == 0) return nil;
    NSMutableDictionary<NSString *, NSData *> *entries = [NSMutableDictionary dictionary];
    if (!TypesetUnzipEntries(templateData, entries)) return nil;
    NSData *documentXml = entries[@"word/document.xml"];
    if (!documentXml) return nil;
    NSData *filledXml = nil;
    if (!TypesetFillDocumentXml(documentXml, typesetType ?: @"general", sections, &filledXml)) return nil;
    entries[@"word/document.xml"] = filledXml;
    NSString *title = sections[@"title"];
    if (title.length == 0) title = sourceName.length > 0 ? sourceName : @"typeset_output";
    NSCharacterSet *illegal = [NSCharacterSet characterSetWithCharactersInString:@"/\\:?\"<>|"];
    title = [[title componentsSeparatedByCharactersInSet:illegal] componentsJoinedByString:@""];
    if (title.length > 80) title = [title substringToIndex:80];
    if (title.length == 0) title = @"typeset_output";
    if (![title.lowercaseString hasSuffix:@".docx"]) title = [title stringByAppendingString:@".docx"];
    NSURL *outputDir = [[NSFileManager.defaultManager URLsForDirectory:NSDocumentDirectory
                                                             inDomains:NSUserDomainMask].firstObject
        URLByAppendingPathComponent:@"typeset" isDirectory:YES];
    [[NSFileManager defaultManager] createDirectoryAtURL:outputDir withIntermediateDirectories:YES attributes:nil error:nil];
    NSURL *outputURL = [outputDir URLByAppendingPathComponent:title];
    NSInteger suffix = 1;
    while ([[NSFileManager defaultManager] fileExistsAtPath:outputURL.path]) {
        NSString *base = [title stringByDeletingPathExtension];
        outputURL = [outputDir URLByAppendingPathComponent:
            [NSString stringWithFormat:@"%@(%ld).docx", base, (long)suffix]];
        suffix++;
    }
    if (!TypesetWriteZip(entries, outputURL)) return nil;
    return outputURL;
}

static int TypesetMaxRId(Document *relsDoc) {
    int max = 0;
    for (Element *rel : TypesetElements(relsDoc->documentElement(), @"Relationship", kTypesetRelNS)) {
        NSString *relId = TypesetAttribute(rel, @"Id");
        if ([relId hasPrefix:@"rId"]) {
            max = MAX(max, [[relId substringFromIndex:3] intValue]);
        }
    }
    return max;
}

static int TypesetMaxDocPrId(Document *docXml) {
    int max = 0;
    for (Element *docPr : TypesetElements(docXml, @"docPr", kTypesetWpNS)) {
        max = MAX(max, [TypesetAttribute(docPr, @"id") intValue]);
    }
    return max;
}

static int TypesetMediaFileCount(NSDictionary<NSString *, NSData *> *entries) {
    int max = 0;
    NSRegularExpression *re = [NSRegularExpression regularExpressionWithPattern:@"word/media/image(\\d+)\\."
                                                                        options:0 error:nil];
    for (NSString *name in entries) {
        NSTextCheckingResult *match = [re firstMatchInString:name options:0
                                                       range:NSMakeRange(0, name.length)];
        if (match.numberOfRanges > 1) {
            max = MAX(max, [[name substringWithRange:[match rangeAtIndex:1]] intValue]);
        }
    }
    return max;
}

static Element *TypesetBuildDrawingElement(Document *document,
                                           NSString *rId,
                                           int docPrId,
                                           NSString *name,
                                           long cx,
                                           long cy) {
    NSString *xml = [NSString stringWithFormat:
        @"<w:drawing xmlns:w=\"%@\" xmlns:wp=\"%@\" xmlns:a=\"%@\" xmlns:r=\"%@\" xmlns:pic=\"%@\">"
         "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
         "<wp:extent cx=\"%ld\" cy=\"%ld\"/>"
         "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>"
         "<wp:docPr id=\"%d\" name=\"%@\"/>"
         "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>"
         "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
         "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"%d\" name=\"%@\"/><pic:cNvPicPr/></pic:nvPicPr>"
         "<pic:blipFill><a:blip r:embed=\"%@\"/></pic:blipFill>"
         "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"%ld\" cy=\"%ld\"/></a:xfrm>"
         "<a:prstGeom prst=\"rect\"/><a:noFill/></pic:spPr></pic:pic></a:graphicData></a:graphic>"
         "</wp:inline></w:drawing>",
        kTypesetWordNS, kTypesetWpNS, kTypesetDrawingNS, kTypesetRelOfficeNS, kTypesetPicNS,
        cx, cy, docPrId, name, docPrId, name, rId, cx, cy];
    AutoPtr<Document> temp = TypesetParseXmlString(xml);
    if (!temp || !temp->documentElement()) return nullptr;
    return static_cast<Element *>(document->importNode(temp->documentElement(), true));
}

static Element *TypesetCloneRunWithDrawing(Element *templateRun, Element *drawing) {
    Element *newRun = TypesetCreateElement(templateRun->ownerDocument(), @"w:r", kTypesetWordNS);
    std::vector<Element *> rPrs = TypesetElements(templateRun, @"rPr", kTypesetWordNS);
    Element *templateRPr = rPrs.empty() ? nullptr : rPrs.front();
    if (templateRPr) {
        newRun->appendChild(templateRPr->cloneNode(true));
    }
    if (drawing) {
        newRun->appendChild(drawing);
    }
    return newRun;
}

static Element *TypesetCloneRunWithText(Element *templateRun, NSString *text) {
    Document *document = templateRun->ownerDocument();
    Element *newRun = TypesetCreateElement(document, @"w:r", kTypesetWordNS);
    std::vector<Element *> rPrs = TypesetElements(templateRun, @"rPr", kTypesetWordNS);
    Element *templateRPr = rPrs.empty() ? nullptr : rPrs.front();
    if (templateRPr) {
        newRun->appendChild(templateRPr->cloneNode(true));
    }
    Element *textNode = TypesetCreateElement(document, @"w:t", kTypesetWordNS);
    TypesetSetAttributeNS(textNode, @"xml:space", @"http://www.w3.org/XML/1998/namespace", @"preserve");
    TypesetSetStringValue(textNode, text ?: @"");
    newRun->appendChild(textNode);
    return newRun;
}

static void TypesetAddImageRelationship(Document *relsDoc, NSString *rId, NSString *target) {
    Element *rel = TypesetCreateElement(relsDoc, @"Relationship", kTypesetRelNS);
    TypesetSetAttribute(rel, @"Id", rId);
    TypesetSetAttribute(rel, @"Type",
                        @"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image");
    TypesetSetAttribute(rel, @"Target", [@"media/" stringByAppendingString:target]);
    relsDoc->documentElement()->appendChild(rel);
}

static void TypesetAddContentTypeIfNeeded(Document *ctDoc, NSString *extension, NSString *mimeType) {
    if (!ctDoc) return;
    Element *root = ctDoc->documentElement();
    for (Node *child = root->firstChild(); child; child = child->nextSibling()) {
        if (child->nodeType() != Node::ELEMENT_NODE || ![TypesetLocalName(child) isEqualToString:@"Default"]) continue;
        Element *node = static_cast<Element *>(child);
        if ([TypesetAttribute(node, @"Extension") caseInsensitiveCompare:extension] == NSOrderedSame) {
            return;
        }
    }
    Element *defaultEl = TypesetCreateElement(ctDoc, @"Default", kTypesetContentTypesNS);
    TypesetSetAttribute(defaultEl, @"Extension", extension);
    TypesetSetAttribute(defaultEl, @"ContentType", mimeType);
    root->appendChild(defaultEl);
}

+ (BOOL)insertImages:(NSDictionary<NSString *, NSDictionary *> *)images intoDocxAtURL:(NSURL *)docxURL {
    if (!docxURL || ![[NSFileManager defaultManager] fileExistsAtPath:docxURL.path]) {
        return NO;
    }
    if (images.count == 0) return YES;
    NSData *zipData = [NSData dataWithContentsOfURL:docxURL];
    if (!zipData) return NO;
    NSMutableDictionary<NSString *, NSData *> *entries = [NSMutableDictionary dictionary];
    if (!TypesetUnzipEntries(zipData, entries)) return NO;
    NSData *documentXml = entries[@"word/document.xml"];
    NSData *relsXml = entries[@"word/_rels/document.xml.rels"];
    if (!documentXml || !relsXml) return NO;
    AutoPtr<Document> docXml = TypesetParseXml(documentXml);
    AutoPtr<Document> relsDoc = TypesetParseXml(relsXml);
    AutoPtr<Document> ctDoc = TypesetParseXml(entries[@"[Content_Types].xml"]);
    if (!docXml || !relsDoc) return NO;
    int nextRId = TypesetMaxRId(relsDoc) + 1;
    int nextDocPrId = TypesetMaxDocPrId(docXml) + 1;
    int nextImageIndex = TypesetMediaFileCount(entries) + 1;
    NSRegularExpression *markerRe = [NSRegularExpression regularExpressionWithPattern:@"\\[图(\\d+)\\]"
                                                                               options:0 error:nil];
    int inserted = 0;
    for (Element *textNode : TypesetElements(docXml, @"t", kTypesetWordNS)) {
        NSString *text = TypesetStringValue(textNode);
        if (text.length == 0) continue;
        NSTextCheckingResult *match = [markerRe firstMatchInString:text options:0
                                                             range:NSMakeRange(0, text.length)];
        if (!match) continue;
        NSString *markerNum = [text substringWithRange:[match rangeAtIndex:1]];
        NSString *marker = [NSString stringWithFormat:@"图%@", markerNum];
        NSDictionary *entry = images[marker];
        if (!entry) continue;
        NSData *imageData = nil;
        id base64 = entry[@"base64"];
        if ([base64 isKindOfClass:[NSString class]]) {
            imageData = [[NSData alloc] initWithBase64EncodedString:base64 options:0];
        } else if ([entry[@"data"] isKindOfClass:[NSData class]]) {
            imageData = entry[@"data"];
        }
        if (!imageData) continue;
        NSString *extension = [entry[@"extension"] isKindOfClass:[NSString class]] ? entry[@"extension"] : @"png";
        NSString *mimeType = [entry[@"mimeType"] isKindOfClass:[NSString class]]
            ? entry[@"mimeType"] : TypesetMimeForExtension(extension);
        long cx = [entry[@"cx"] respondsToSelector:@selector(longLongValue)]
            ? [entry[@"cx"] longLongValue] : kTypesetDefaultEmu;
        long cy = [entry[@"cy"] respondsToSelector:@selector(longLongValue)]
            ? [entry[@"cy"] longLongValue] : kTypesetDefaultEmu;
        if (cx <= 0) cx = kTypesetDefaultEmu;
        if (cy <= 0) cy = kTypesetDefaultEmu;

        Node *runNode = textNode->parentNode();
        while (runNode && ![TypesetLocalName(runNode) isEqualToString:@"r"]) {
            runNode = runNode->parentNode();
        }
        Element *runElem = runNode && runNode->nodeType() == Node::ELEMENT_NODE
            ? static_cast<Element *>(runNode) : nullptr;
        Node *paraNode = runElem ? runElem->parentNode() : nullptr;
        Element *paraElem = paraNode && paraNode->nodeType() == Node::ELEMENT_NODE
            ? static_cast<Element *>(paraNode) : nullptr;
        if (!runElem || !paraElem) continue;

        NSString *fullMarker = [text substringWithRange:match.range];
        NSString *beforeText = [text substringToIndex:match.range.location];
        NSString *afterText = [text substringFromIndex:NSMaxRange(match.range)];
        NSString *rId = [NSString stringWithFormat:@"rId%d", nextRId++];
        int docPrId = nextDocPrId++;
        NSString *imageFileName = [NSString stringWithFormat:@"image%d.%@", nextImageIndex++, extension];
        Element *drawing = TypesetBuildDrawingElement(docXml, rId, docPrId, fullMarker, cx, cy);
        if (!drawing) continue;

        NSUInteger insertIndex = TypesetIndexOfChild(paraElem, runElem) + 1;
        if (afterText.length > 0) {
            Element *afterRun = TypesetCloneRunWithText(runElem, afterText);
            TypesetInsertChild(paraElem, afterRun, insertIndex++);
        }
        Element *drawRun = TypesetCloneRunWithDrawing(runElem, drawing);
        TypesetInsertChild(paraElem, drawRun, insertIndex);
        if (beforeText.length > 0) {
            TypesetSetStringValue(textNode, beforeText);
        } else {
            paraElem->removeChild(runElem);
        }
        TypesetAddImageRelationship(relsDoc, rId, imageFileName);
        TypesetAddContentTypeIfNeeded(ctDoc, extension, mimeType);
        entries[[@"word/media/" stringByAppendingString:imageFileName]] = imageData;
        inserted++;
    }
    if (inserted == 0) return YES;
    entries[@"word/document.xml"] = TypesetSerializeXml(docXml);
    entries[@"word/_rels/document.xml.rels"] = TypesetSerializeXml(relsDoc);
    if (ctDoc) {
        entries[@"[Content_Types].xml"] = TypesetSerializeXml(ctDoc);
    }
    [[NSFileManager defaultManager] removeItemAtURL:docxURL error:nil];
    return TypesetWriteZip(entries, docxURL);
}

+ (BOOL)copyDocxToOriginalURL:(NSURL *)docxURL originalURL:(NSURL *)originalURL {
    if (!docxURL || ![[NSFileManager defaultManager] fileExistsAtPath:docxURL.path]) {
        return NO;
    }
    if (!originalURL) return NO;
    if ([originalURL isFileURL]) {
        NSError *error = nil;
        if ([[NSFileManager defaultManager] fileExistsAtPath:originalURL.path]) {
            [[NSFileManager defaultManager] removeItemAtURL:originalURL error:nil];
        }
        return [[NSFileManager defaultManager] copyItemAtURL:docxURL toURL:originalURL error:&error];
    }
    NSData *data = [NSData dataWithContentsOfURL:docxURL];
    if (!data) return NO;
    if ([originalURL.scheme isEqualToString:@"file"]) {
        return [data writeToURL:originalURL atomically:YES];
    }
    // content:// and other providers: best-effort via NSFileCoordinator
    __block NSError *coordError = nil;
    __block BOOL success = NO;
    NSFileCoordinator *coordinator = [[NSFileCoordinator alloc] init];
    [coordinator coordinateWritingItemAtURL:originalURL
                                      options:NSFileCoordinatorWritingForReplacing
                                        error:&coordError
                                   byAccessor:^(NSURL *newURL) {
        success = [data writeToURL:newURL atomically:YES];
    }];
    return success && !coordError;
}

@end

// vim:set shiftwidth=4 softtabstop=4 expandtab:
