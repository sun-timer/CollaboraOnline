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

static NSString * const kTypesetWordNS = @"http://schemas.openxmlformats.org/wordprocessingml/2006/main";
static NSString * const kTypesetDrawingNS = @"http://schemas.openxmlformats.org/drawingml/2006/main";
static NSString * const kTypesetWpNS = @"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
static NSString * const kTypesetRelNS = @"http://schemas.openxmlformats.org/package/2006/relationships";
static NSString * const kTypesetRelOfficeNS = @"http://schemas.openxmlformats.org/officeDocument/2006/relationships";
static NSString * const kTypesetPicNS = @"http://schemas.openxmlformats.org/drawingml/2006/picture";
static NSString * const kTypesetContentTypesNS = @"http://schemas.openxmlformats.org/package/2006/content-types";
static const long kTypesetDefaultEmu = 5L * 914400L;

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

static NSString *TypesetParagraphText(NSXMLElement *paragraph) {
    NSMutableString *text = [NSMutableString string];
    for (NSXMLElement *node in [paragraph elementsForLocalName:@"t" URI:kTypesetWordNS]) {
        if (node.stringValue.length > 0) {
            [text appendString:node.stringValue];
        }
    }
    return [[text copy] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

static void TypesetReplaceParagraphText(NSXMLElement *paragraph, NSString *newText) {
    NSArray<NSXMLElement *> *runs = [paragraph elementsForLocalName:@"r" URI:kTypesetWordNS];
    NSXMLElement *templateRun = runs.firstObject;
    NSXMLElement *templateRPr = nil;
    if (templateRun) {
        NSArray<NSXMLElement *> *rPrs = [templateRun elementsForLocalName:@"rPr" URI:kTypesetWordNS];
        templateRPr = rPrs.firstObject;
    }
    for (NSXMLElement *run in [runs copy]) {
        [paragraph removeChildAtIndex:[paragraph indexOfChild:run]];
    }
    NSXMLElement *newRun = [NSXMLElement elementWithName:@"w:r" URI:kTypesetWordNS];
    if (templateRPr) {
        [newRun addChild:[templateRPr copy]];
    }
    NSXMLElement *textNode = [NSXMLElement elementWithName:@"w:t" URI:kTypesetWordNS];
    [textNode addAttribute:[NSXMLNode attributeWithName:@"xml:space" stringValue:@"preserve"]];
    [textNode setStringValue:newText ?: @""];
    [newRun addChild:textNode];
    [paragraph addChild:newRun];
}

static NSXMLElement *TypesetCloneParagraph(NSXMLElement *source, NSString *text) {
    NSXMLElement *newP = [NSXMLElement elementWithName:@"w:p" URI:kTypesetWordNS];
    NSArray<NSXMLElement *> *pPrs = [source elementsForLocalName:@"pPr" URI:kTypesetWordNS];
    if (pPrs.firstObject) {
        [newP addChild:[pPrs.firstObject copy]];
    }
    TypesetReplaceParagraphText(newP, text);
    return newP;
}

static BOOL TypesetFillDocumentXml(NSData *documentXml,
                                   NSString *typesetType,
                                   NSDictionary<NSString *, NSString *> *sections,
                                   NSData **outData) {
    NSError *error = nil;
    NSXMLDocument *doc = [[NSXMLDocument alloc] initWithData:documentXml options:0 error:&error];
    if (!doc) return NO;
    NSArray<NSXMLElement *> *bodies = [doc elementsForLocalName:@"body" URI:kTypesetWordNS];
    NSXMLElement *body = bodies.firstObject;
    if (!body) return NO;
    NSArray<NSDictionary *> *entries = TypesetPlaceholderMap()[typesetType] ?: TypesetPlaceholderMap()[@"general"];
    NSMutableSet<NSString *> *usedKeys = [NSMutableSet set];
    NSMutableArray<NSXMLElement *> *paragraphs = [NSMutableArray array];
    for (NSXMLNode *child in body.children) {
        if (child.kind == NSXMLElementKind && [child.localName isEqualToString:@"p"]) {
            [paragraphs addObject:(NSXMLElement *)child];
        }
    }
    for (NSXMLElement *paragraph in paragraphs) {
        NSString *paraText = TypesetParagraphText(paragraph);
        if (paraText.length == 0) continue;
        NSString *sectionKey = TypesetMatchPlaceholder(paraText, entries, usedKeys);
        if (!sectionKey) continue;
        [usedKeys addObject:sectionKey];
        NSString *content = sections[sectionKey];
        if (content.length == 0) continue;
        NSArray<NSString *> *parts = [content componentsSeparatedByString:@"\n\n"];
        TypesetReplaceParagraphText(paragraph, parts.firstObject ?: @"");
        NSXMLElement *anchor = paragraph;
        for (NSUInteger i = 1; i < parts.count; i++) {
            NSString *segment = [parts[i] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (segment.length == 0) continue;
            NSXMLElement *newP = TypesetCloneParagraph(paragraph, segment);
            [body insertChild:newP atIndex:[body indexOfChild:anchor] + 1];
            anchor = newP;
        }
    }
    NSData *xmlData = [doc XMLDataWithOptions:NSXMLNodePrettyPrint];
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
    NSError *error = nil;
    NSXMLDocument *relsDoc = [[NSXMLDocument alloc] initWithData:relsXml options:0 error:&error];
    if (!relsDoc) return @{};
    NSMutableDictionary<NSString *, NSString *> *relMap = [NSMutableDictionary dictionary];
    for (NSXMLElement *rel in [relsDoc.rootElement elementsForLocalName:@"Relationship" URI:kTypesetRelNS]) {
        NSString *type = [[rel attributeForName:@"Type"] stringValue];
        NSString *target = [[rel attributeForName:@"Target"] stringValue];
        NSString *relId = [[rel attributeForName:@"Id"] stringValue];
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

static void TypesetCollectParagraphContent(NSXMLElement *paragraph,
                                           NSDictionary<NSString *, NSString *> *relMap,
                                           NSDictionary<NSString *, NSData *> *entries,
                                           NSMutableString *paraText,
                                           NSMutableDictionary<NSString *, NSDictionary *> *images,
                                           int *imgCounter) {
    for (NSXMLElement *run in [paragraph elementsForLocalName:@"r" URI:kTypesetWordNS]) {
        for (NSXMLElement *textNode in [run elementsForLocalName:@"t" URI:kTypesetWordNS]) {
            if (textNode.stringValue.length > 0) {
                [paraText appendString:textNode.stringValue];
            }
        }
        for (NSXMLElement *drawing in [run elementsForLocalName:@"drawing" URI:kTypesetWordNS]) {
            for (NSXMLElement *blip in [drawing elementsForLocalName:@"blip" URI:kTypesetDrawingNS]) {
                NSString *embed = [[blip attributeForName:@"embed" URI:kTypesetRelOfficeNS] stringValue];
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
    NSError *error = nil;
    NSXMLDocument *doc = [[NSXMLDocument alloc] initWithData:documentXml options:0 error:&error];
    if (!doc) return nil;
    NSXMLElement *body = [[doc elementsForLocalName:@"body" URI:kTypesetWordNS] firstObject];
    if (!body) return nil;
    NSDictionary<NSString *, NSString *> *relMap =
        TypesetBuildImageRelMap(entries[@"word/_rels/document.xml.rels"]);
    NSMutableArray<NSString *> *paragraphs = [NSMutableArray array];
    NSMutableString *fullText = [NSMutableString string];
    NSMutableDictionary<NSString *, NSDictionary *> *images = [NSMutableDictionary dictionary];
    int imgCounter = 0;
    for (NSXMLNode *child in body.children) {
        if (child.kind != NSXMLElementKind || ![child.localName isEqualToString:@"p"]) continue;
        NSMutableString *paraText = [NSMutableString string];
        TypesetCollectParagraphContent((NSXMLElement *)child, relMap, entries,
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

@implementation TypesetService

+ (NSDictionary *)extractStructuredFromFile:(NSURL *)fileURL {
    if (!fileURL || ![[NSFileManager defaultManager] fileExistsAtPath:fileURL.path]) {
        return nil;
    }
    NSData *data = [NSData dataWithContentsOfURL:fileURL];
    if (!data) return nil;
    NSMutableDictionary<NSString *, NSData *> *entries = [NSMutableDictionary dictionary];
    if (!TypesetUnzipEntries(data, entries)) return nil;
    return TypesetExtractStructuredFromEntries(entries);
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

static int TypesetMaxRId(NSXMLDocument *relsDoc) {
    int max = 0;
    for (NSXMLElement *rel in [relsDoc.rootElement elementsForLocalName:@"Relationship" URI:kTypesetRelNS]) {
        NSString *relId = [[rel attributeForName:@"Id"] stringValue];
        if ([relId hasPrefix:@"rId"]) {
            max = MAX(max, [[relId substringFromIndex:3] intValue]);
        }
    }
    return max;
}

static int TypesetMaxDocPrId(NSXMLDocument *docXml) {
    int max = 0;
    for (NSXMLElement *docPr in [docXml elementsForLocalName:@"docPr" URI:kTypesetWpNS]) {
        max = MAX(max, [[[docPr attributeForName:@"id"] stringValue] intValue]);
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

static NSXMLElement *TypesetBuildDrawingElement(NSString *rId, int docPrId, NSString *name, long cx, long cy) {
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
    NSXMLDocument *temp = [[NSXMLDocument alloc] initWithXMLString:xml options:0 error:nil];
    return temp.rootElement;
}

static NSXMLElement *TypesetCloneRunWithDrawing(NSXMLElement *templateRun, NSXMLElement *drawing) {
    NSXMLElement *newRun = [NSXMLElement elementWithName:@"w:r" URI:kTypesetWordNS];
    NSXMLElement *templateRPr = [[templateRun elementsForLocalName:@"rPr" URI:kTypesetWordNS] firstObject];
    if (templateRPr) {
        [newRun addChild:[templateRPr copy]];
    }
    if (drawing) {
        [newRun addChild:drawing];
    }
    return newRun;
}

static NSXMLElement *TypesetCloneRunWithText(NSXMLElement *templateRun, NSString *text) {
    NSXMLElement *newRun = [NSXMLElement elementWithName:@"w:r" URI:kTypesetWordNS];
    NSXMLElement *templateRPr = [[templateRun elementsForLocalName:@"rPr" URI:kTypesetWordNS] firstObject];
    if (templateRPr) {
        [newRun addChild:[templateRPr copy]];
    }
    NSXMLElement *textNode = [NSXMLElement elementWithName:@"w:t" URI:kTypesetWordNS];
    [textNode addAttribute:[NSXMLNode attributeWithName:@"xml:space" stringValue:@"preserve"]];
    [textNode setStringValue:text ?: @""];
    [newRun addChild:textNode];
    return newRun;
}

static void TypesetAddImageRelationship(NSXMLDocument *relsDoc, NSString *rId, NSString *target) {
    NSXMLElement *rel = [NSXMLElement elementWithName:@"Relationship" URI:kTypesetRelNS];
    [rel addAttribute:[NSXMLNode attributeWithName:@"Id" stringValue:rId]];
    [rel addAttribute:[NSXMLNode attributeWithName:@"Type"
                                      stringValue:@"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"]];
    [rel addAttribute:[NSXMLNode attributeWithName:@"Target"
                                      stringValue:[@"media/" stringByAppendingString:target]]];
    [relsDoc.rootElement addChild:rel];
}

static void TypesetAddContentTypeIfNeeded(NSXMLDocument *ctDoc, NSString *extension, NSString *mimeType) {
    if (!ctDoc) return;
    for (NSXMLElement *node in ctDoc.rootElement.children) {
        if (![node.localName isEqualToString:@"Default"]) continue;
        if ([[[node attributeForName:@"Extension"] stringValue] caseInsensitiveCompare:extension] == NSOrderedSame) {
            return;
        }
    }
    NSXMLElement *defaultEl = [NSXMLElement elementWithName:@"Default" URI:kTypesetContentTypesNS];
    [defaultEl addAttribute:[NSXMLNode attributeWithName:@"Extension" stringValue:extension]];
    [defaultEl addAttribute:[NSXMLNode attributeWithName:@"ContentType" stringValue:mimeType]];
    [ctDoc.rootElement addChild:defaultEl];
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
    NSError *error = nil;
    NSXMLDocument *docXml = [[NSXMLDocument alloc] initWithData:documentXml options:0 error:&error];
    NSXMLDocument *relsDoc = [[NSXMLDocument alloc] initWithData:relsXml options:0 error:&error];
    NSXMLDocument *ctDoc = entries[@"[Content_Types].xml"]
        ? [[NSXMLDocument alloc] initWithData:entries[@"[Content_Types].xml"] options:0 error:nil]
        : nil;
    if (!docXml || !relsDoc) return NO;
    int nextRId = TypesetMaxRId(relsDoc) + 1;
    int nextDocPrId = TypesetMaxDocPrId(docXml) + 1;
    int nextImageIndex = TypesetMediaFileCount(entries) + 1;
    NSRegularExpression *markerRe = [NSRegularExpression regularExpressionWithPattern:@"\\[图(\\d+)\\]"
                                                                               options:0 error:nil];
    int inserted = 0;
    for (NSXMLElement *textNode in [docXml elementsForLocalName:@"t" URI:kTypesetWordNS]) {
        NSString *text = textNode.stringValue;
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

        NSXMLElement *runElem = (NSXMLElement *)textNode.parent;
        while (runElem && ![runElem.localName isEqualToString:@"r"]) {
            runElem = (NSXMLElement *)runElem.parent;
        }
        NSXMLElement *paraElem = runElem ? (NSXMLElement *)runElem.parent : nil;
        if (!runElem || !paraElem) continue;

        NSString *fullMarker = [text substringWithRange:match.range];
        NSString *beforeText = [text substringToIndex:match.range.location];
        NSString *afterText = [text substringFromIndex:NSMaxRange(match.range)];
        NSString *rId = [NSString stringWithFormat:@"rId%d", nextRId++];
        int docPrId = nextDocPrId++;
        NSString *imageFileName = [NSString stringWithFormat:@"image%d.%@", nextImageIndex++, extension];
        NSXMLElement *drawing = TypesetBuildDrawingElement(rId, docPrId, fullMarker, cx, cy);
        if (!drawing) continue;

        NSUInteger insertIndex = [paraElem indexOfChild:runElem] + 1;
        if (afterText.length > 0) {
            NSXMLElement *afterRun = TypesetCloneRunWithText(runElem, afterText);
            [paraElem insertChild:afterRun atIndex:insertIndex++];
        }
        NSXMLElement *drawRun = TypesetCloneRunWithDrawing(runElem, drawing);
        [paraElem insertChild:drawRun atIndex:insertIndex];
        if (beforeText.length > 0) {
            [textNode setStringValue:beforeText];
        } else {
            [paraElem removeChildAtIndex:[paraElem indexOfChild:runElem]];
        }
        TypesetAddImageRelationship(relsDoc, rId, imageFileName);
        TypesetAddContentTypeIfNeeded(ctDoc, extension, mimeType);
        entries[[@"word/media/" stringByAppendingString:imageFileName]] = imageData;
        inserted++;
    }
    if (inserted == 0) return YES;
    entries[@"word/document.xml"] = [docXml XMLDataWithOptions:NSXMLNodePrettyPrint];
    entries[@"word/_rels/document.xml.rels"] = [relsDoc XMLDataWithOptions:NSXMLNodePrettyPrint];
    if (ctDoc) {
        entries[@"[Content_Types].xml"] = [ctDoc XMLDataWithOptions:NSXMLNodePrettyPrint];
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
