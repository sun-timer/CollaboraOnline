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

@implementation TypesetService

+ (NSDictionary *)extractStructuredFromFile:(NSURL *)fileURL {
    if (!fileURL || ![[NSFileManager defaultManager] fileExistsAtPath:fileURL.path]) {
        return nil;
    }
    NSData *data = [NSData dataWithContentsOfURL:fileURL];
    if (!data) return nil;
    NSMutableDictionary<NSString *, NSData *> *entries = [NSMutableDictionary dictionary];
    if (!TypesetUnzipEntries(data, entries)) return nil;
    NSData *documentXml = entries[@"word/document.xml"];
    if (!documentXml) return nil;
    NSError *error = nil;
    NSXMLDocument *doc = [[NSXMLDocument alloc] initWithData:documentXml options:0 error:&error];
    if (!doc) return nil;
    NSXMLElement *body = [[doc elementsForLocalName:@"body" URI:kTypesetWordNS] firstObject];
    if (!body) return nil;
    NSMutableArray<NSString *> *paragraphs = [NSMutableArray array];
    NSMutableString *fullText = [NSMutableString string];
    for (NSXMLNode *child in body.children) {
        if (child.kind != NSXMLElementKind || ![child.localName isEqualToString:@"p"]) continue;
        NSString *para = TypesetParagraphText((NSXMLElement *)child);
        if (para.length == 0) continue;
        [paragraphs addObject:para];
        if (fullText.length > 0) [fullText appendString:@"\n\n"];
        [fullText appendString:para];
    }
    if (paragraphs.count == 0) return nil;
    return @{
        @"fullText": fullText,
        @"paragraphs": paragraphs,
    };
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
