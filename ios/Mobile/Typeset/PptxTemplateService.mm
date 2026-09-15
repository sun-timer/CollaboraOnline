// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * PPTX fill using Typeset minizip unzip/zip + Poco DOM (DrawingML a:t).
 * Do not port Android java.util.zip 1:1.
 */

#import "PptxTemplateService.h"

#import "minizip/unzip.h"
#import "minizip/zip.h"

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

@implementation PptxTemplateInfo
@end

static NSString * const kPptxErrorDomain = @"com.xunlong.xloffice.pptx";

static NSURL *PptxTemplatesRoot(void) {
    NSBundle *bundle = [NSBundle mainBundle];
    NSURL *url = [bundle URLForResource:@"impress-templates" withExtension:nil];
    if (url) {
        return url;
    }
    url = [bundle URLForResource:@"index" withExtension:@"json" subdirectory:@"impress-templates"];
    if (url) {
        return [url URLByDeletingLastPathComponent];
    }
    url = [bundle URLForResource:@"index" withExtension:@"json" subdirectory:@"impress"];
    if (url) {
        return [url URLByDeletingLastPathComponent];
    }
    return nil;
}

static BOOL PptxUnzipEntries(NSData *zipData, NSMutableDictionary<NSString *, NSData *> *entries) {
    NSString *tempPath = [NSTemporaryDirectory() stringByAppendingPathComponent:[[NSUUID UUID] UUIDString]];
    if (![zipData writeToFile:tempPath atomically:YES]) {
        return NO;
    }
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
        if (unzOpenCurrentFile(zip) != UNZ_OK) {
            continue;
        }
        NSMutableData *data = [NSMutableData dataWithLength:fileInfo.uncompressed_size];
        int read = unzReadCurrentFile(zip, data.mutableBytes, (unsigned)fileInfo.uncompressed_size);
        unzCloseCurrentFile(zip);
        if (read >= 0) {
            [data setLength:(NSUInteger)MAX(read, 0)];
            entries[@(filename)] = data;
        }
    } while (unzGoToNextFile(zip) == UNZ_OK);
    unzClose(zip);
    [[NSFileManager defaultManager] removeItemAtPath:tempPath error:nil];
    return entries.count > 0;
}

static BOOL PptxWriteZip(NSDictionary<NSString *, NSData *> *entries, NSURL *outputURL) {
    zipFile zip = zipOpen(outputURL.path.UTF8String, APPEND_STATUS_CREATE);
    if (!zip) {
        return NO;
    }
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

static const char *kPptxDrawingNS = "http://schemas.openxmlformats.org/drawingml/2006/main";

static std::string PptxUTF8(NSString *value) {
    return value ? std::string(value.UTF8String ?: "") : std::string();
}

static NSString *PptxNSString(const std::string& value) {
    return [[NSString alloc] initWithBytes:value.data()
                                   length:value.size()
                                 encoding:NSUTF8StringEncoding] ?: @"";
}

static AutoPtr<Document> PptxParseXml(NSData *data) {
    if (!data) {
        return nullptr;
    }
    try {
        DOMParser parser;
        return AutoPtr<Document>(parser.parseMemory(static_cast<const char *>(data.bytes), data.length));
    } catch (const Poco::Exception&) {
        return nullptr;
    } catch (const std::exception&) {
        return nullptr;
    }
}

static NSData *PptxSerializeXml(Document *document) {
    if (!document) {
        return nil;
    }
    try {
        std::ostringstream stream;
        DOMWriter writer;
        writer.setOptions(XMLWriter::WRITE_XML_DECLARATION);
        writer.writeNode(stream, document);
        const std::string xml = stream.str();
        return [NSData dataWithBytes:xml.data() length:xml.size()];
    } catch (const Poco::Exception&) {
        return nil;
    } catch (const std::exception&) {
        return nil;
    }
}

static std::vector<Element *> PptxElementsNS(Node *node, const char *localName, const char *ns) {
    std::vector<Element *> elements;
    if (!node) {
        return elements;
    }
    AutoPtr<NodeList> list;
    if (node->nodeType() == Node::DOCUMENT_NODE) {
        list = static_cast<Document *>(node)->getElementsByTagNameNS(ns, localName);
    } else if (node->nodeType() == Node::ELEMENT_NODE) {
        list = static_cast<Element *>(node)->getElementsByTagNameNS(ns, localName);
    }
    if (!list) {
        return elements;
    }
    elements.reserve(list->length());
    for (unsigned long i = 0; i < list->length(); ++i) {
        Node *item = list->item(i);
        if (item && item->nodeType() == Node::ELEMENT_NODE) {
            elements.push_back(static_cast<Element *>(item));
        }
    }
    return elements;
}

static NSString *PptxElementText(Element *element) {
    return element ? PptxNSString(element->innerText()) : @"";
}

static void PptxSetElementText(Element *element, NSString *value) {
    if (!element) {
        return;
    }
    while (Node *child = element->firstChild()) {
        element->removeChild(child);
    }
    Document *owner = element->ownerDocument();
    if (!owner) {
        return;
    }
    element->appendChild(owner->createTextNode(PptxUTF8(value ?: @"")));
}

static NSString *PptxXmlEscape(NSString *value) {
    if (value.length == 0) {
        return @"";
    }
    NSString *escaped = [value stringByReplacingOccurrencesOfString:@"&" withString:@"&amp;"];
    escaped = [escaped stringByReplacingOccurrencesOfString:@"<" withString:@"&lt;"];
    escaped = [escaped stringByReplacingOccurrencesOfString:@">" withString:@"&gt;"];
    return escaped;
}

static NSString *PptxLookupPlaceholder(NSString *key, NSDictionary<NSString *, NSString *> *placeholders) {
    NSString *value = placeholders[key];
    if (value) {
        return value;
    }
    NSRegularExpression *indexed = [NSRegularExpression regularExpressionWithPattern:@"^(.*)\\[(\\d+)\\]$"
                                                                             options:0
                                                                               error:nil];
    NSTextCheckingResult *match = [indexed firstMatchInString:key options:0 range:NSMakeRange(0, key.length)];
    if (match && match.numberOfRanges == 3) {
        NSString *base = [key substringWithRange:[match rangeAtIndex:1]];
        NSInteger index = [[key substringWithRange:[match rangeAtIndex:2]] integerValue];
        NSString *indexedKey = [NSString stringWithFormat:@"%@[%ld]", base, (long)index];
        value = placeholders[indexedKey];
        if (value) {
            return value;
        }
    }
    return @"";
}

static NSString *PptxReplacePlaceholdersInText(NSString *joined, NSDictionary<NSString *, NSString *> *placeholders) {
    if (joined.length == 0 || [joined rangeOfString:@"{{"].location == NSNotFound) {
        return joined;
    }
    NSRegularExpression *phRe = [NSRegularExpression regularExpressionWithPattern:@"\\{\\{([^{}]+)\\}\\}"
                                                                          options:0
                                                                            error:nil];
    NSMutableString *replaced = [NSMutableString string];
    __block NSUInteger last = 0;
    [phRe enumerateMatchesInString:joined options:0 range:NSMakeRange(0, joined.length) usingBlock:^(NSTextCheckingResult *result, NSMatchingFlags flags, BOOL *stop) {
        if (result.range.location > last) {
            [replaced appendString:[joined substringWithRange:NSMakeRange(last, result.range.location - last)]];
        }
        NSString *key = [[joined substringWithRange:[result rangeAtIndex:1]] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        [replaced appendString:PptxLookupPlaceholder(key, placeholders)];
        last = result.range.location + result.range.length;
    }];
    if (last < joined.length) {
        [replaced appendString:[joined substringFromIndex:last]];
    }
    return replaced;
}

static NSString *PptxFillParagraphBody(NSString *body, NSDictionary<NSString *, NSString *> *placeholders) {
    NSRegularExpression *runRe = [NSRegularExpression regularExpressionWithPattern:@"(<a:t(?:\\s[^>]*)?>)([^<]*)(</a:t>)"
                                                                           options:0
                                                                             error:nil];
    NSArray<NSTextCheckingResult *> *runs = [runRe matchesInString:body options:0 range:NSMakeRange(0, body.length)];
    if (runs.count == 0) {
        return body;
    }
    NSMutableString *joined = [NSMutableString string];
    for (NSTextCheckingResult *run in runs) {
        if (run.numberOfRanges >= 3) {
            [joined appendString:[body substringWithRange:[run rangeAtIndex:2]]];
        }
    }
    if ([joined rangeOfString:@"{{"].location == NSNotFound) {
        return body;
    }
    NSRegularExpression *phRe = [NSRegularExpression regularExpressionWithPattern:@"\\{\\{([^{}]+)\\}\\}"
                                                                          options:0
                                                                            error:nil];
    NSMutableString *replaced = [NSMutableString string];
    __block NSUInteger last = 0;
    __block BOOL changed = NO;
    [phRe enumerateMatchesInString:joined options:0 range:NSMakeRange(0, joined.length) usingBlock:^(NSTextCheckingResult *result, NSMatchingFlags flags, BOOL *stop) {
        if (result.range.location > last) {
            [replaced appendString:[joined substringWithRange:NSMakeRange(last, result.range.location - last)]];
        }
        NSString *key = [[joined substringWithRange:[result rangeAtIndex:1]] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        [replaced appendString:PptxLookupPlaceholder(key, placeholders)];
        last = result.range.location + result.range.length;
        changed = YES;
    }];
    if (last < joined.length) {
        [replaced appendString:[joined substringFromIndex:last]];
    }
    if (!changed) {
        return body;
    }
    NSString *finalText = PptxXmlEscape(replaced);
    NSMutableString *newBody = [NSMutableString string];
    NSUInteger cursor = 0;
    NSInteger runIndex = 0;
    for (NSTextCheckingResult *run in runs) {
        if (run.range.location > cursor) {
            [newBody appendString:[body substringWithRange:NSMakeRange(cursor, run.range.location - cursor)]];
        }
        NSString *open = [body substringWithRange:[run rangeAtIndex:1]];
        NSString *close = [body substringWithRange:[run rangeAtIndex:3]];
        [newBody appendString:open];
        [newBody appendString:runIndex == 0 ? finalText : @""];
        [newBody appendString:close];
        cursor = run.range.location + run.range.length;
        runIndex++;
    }
    if (cursor < body.length) {
        [newBody appendString:[body substringFromIndex:cursor]];
    }
    return newBody;
}

static NSData *PptxFillSlideBytesRegex(NSData *slideXml, NSDictionary<NSString *, NSString *> *placeholders) {
    NSString *xml = [[NSString alloc] initWithData:slideXml encoding:NSUTF8StringEncoding];
    if (![xml containsString:@"{{"]) {
        return slideXml;
    }
    NSRegularExpression *pRe = [NSRegularExpression regularExpressionWithPattern:@"(<a:p\\b[^>]*>)(.*?)(</a:p>)"
                                                                         options:NSRegularExpressionDotMatchesLineSeparators
                                                                           error:nil];
    NSMutableString *out = [NSMutableString string];
    __block NSUInteger last = 0;
    [pRe enumerateMatchesInString:xml options:0 range:NSMakeRange(0, xml.length) usingBlock:^(NSTextCheckingResult *result, NSMatchingFlags flags, BOOL *stop) {
        if (result.range.location > last) {
            [out appendString:[xml substringWithRange:NSMakeRange(last, result.range.location - last)]];
        }
        NSString *open = [xml substringWithRange:[result rangeAtIndex:1]];
        NSString *body = [xml substringWithRange:[result rangeAtIndex:2]];
        NSString *close = [xml substringWithRange:[result rangeAtIndex:3]];
        [out appendString:open];
        [out appendString:PptxFillParagraphBody(body, placeholders)];
        [out appendString:close];
        last = result.range.location + result.range.length;
    }];
    if (last < xml.length) {
        [out appendString:[xml substringFromIndex:last]];
    }
    return [out dataUsingEncoding:NSUTF8StringEncoding];
}

static NSData *PptxFillSlideBytes(NSData *slideXml, NSDictionary<NSString *, NSString *> *placeholders) {
    if (!slideXml || placeholders.count == 0) {
        return slideXml;
    }
    AutoPtr<Document> document = PptxParseXml(slideXml);
    if (!document) {
        return PptxFillSlideBytesRegex(slideXml, placeholders);
    }
    std::vector<Element *> paragraphs = PptxElementsNS(document, "p", kPptxDrawingNS);
    BOOL changed = NO;
    for (Element *paragraph : paragraphs) {
        std::vector<Element *> texts = PptxElementsNS(paragraph, "t", kPptxDrawingNS);
        if (texts.empty()) {
            continue;
        }
        NSMutableString *joined = [NSMutableString string];
        for (Element *textNode : texts) {
            [joined appendString:PptxElementText(textNode)];
        }
        if ([joined rangeOfString:@"{{"].location == NSNotFound) {
            continue;
        }
        NSString *replaced = PptxReplacePlaceholdersInText(joined, placeholders);
        PptxSetElementText(texts[0], replaced);
        for (size_t i = 1; i < texts.size(); ++i) {
            PptxSetElementText(texts[i], @"");
        }
        changed = YES;
    }
    if (!changed) {
        return slideXml;
    }
    NSData *serialized = PptxSerializeXml(document);
    return serialized ?: PptxFillSlideBytesRegex(slideXml, placeholders);
}

static NSSet<NSString *> *PptxCollectPlaceholderKeys(NSString *xml) {
    NSMutableSet<NSString *> *keys = [NSMutableSet set];
    NSRegularExpression *phRe = [NSRegularExpression regularExpressionWithPattern:@"\\{\\{([^{}]+)\\}\\}"
                                                                          options:0
                                                                            error:nil];
    [phRe enumerateMatchesInString:xml options:0 range:NSMakeRange(0, xml.length) usingBlock:^(NSTextCheckingResult *result, NSMatchingFlags flags, BOOL *stop) {
        NSString *key = [[xml substringWithRange:[result rangeAtIndex:1]] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        if (key.length > 0) {
            [keys addObject:key];
        }
    }];
    return keys;
}

static NSSet<NSString *> *PptxExtractPlaceholderKeys(NSData *slideXml) {
    AutoPtr<Document> document = PptxParseXml(slideXml);
    if (document) {
        NSMutableSet<NSString *> *keys = [NSMutableSet set];
        std::vector<Element *> texts = PptxElementsNS(document, "t", kPptxDrawingNS);
        for (Element *textNode : texts) {
            [keys unionSet:PptxCollectPlaceholderKeys(PptxElementText(textNode))];
        }
        if (keys.count > 0) {
            return keys;
        }
    }
    NSString *xml = [[NSString alloc] initWithData:slideXml encoding:NSUTF8StringEncoding] ?: @"";
    return PptxCollectPlaceholderKeys(xml);
}

static NSInteger PptxCountContentPointSlots(NSSet<NSString *> *keys) {
    NSInteger maxIndex = -1;
    for (NSString *key in keys) {
        if ([key hasPrefix:@"content_points["]) {
            NSString *inner = [key stringByReplacingOccurrencesOfString:@"content_points[" withString:@""];
            inner = [inner stringByReplacingOccurrencesOfString:@"]" withString:@""];
            NSInteger idx = [inner integerValue];
            if (idx > maxIndex) {
                maxIndex = idx;
            }
        }
    }
    return maxIndex >= 0 ? maxIndex + 1 : 0;
}

typedef NS_ENUM(NSInteger, PptxSlideRole) {
    PptxSlideRoleUnknown = 0,
    PptxSlideRoleCover,
    PptxSlideRoleToc,
    PptxSlideRoleSectionDivider,
    PptxSlideRoleContent,
    PptxSlideRoleEnd,
};

static PptxSlideRole PptxClassifyRole(NSSet<NSString *> *ph, NSInteger pointCount, NSInteger slideIndex, NSInteger totalSlides) {
    BOOL hasToc = [ph containsObject:@"toc_content"];
    BOOL hasDivider = [ph containsObject:@"section_number"] && [ph containsObject:@"section_overview"];
    BOOL hasContent = pointCount > 0 || [ph containsObject:@"content_points[0]"];
    BOOL hasTitle = [ph containsObject:@"title"];
    BOOL hasSubtitle = [ph containsObject:@"subtitle"];
    BOOL hasAuthor = [ph containsObject:@"author"];
    BOOL hasDate = [ph containsObject:@"date"];
    BOOL hasContact = [ph containsObject:@"contact_info"];
    if (hasToc) return PptxSlideRoleToc;
    if (hasDivider) return PptxSlideRoleSectionDivider;
    if (hasContent) return PptxSlideRoleContent;
    if (hasTitle && hasSubtitle) return PptxSlideRoleCover;
    if (hasContact || (hasAuthor && hasDate && slideIndex == totalSlides)) return PptxSlideRoleEnd;
    if (hasAuthor && hasDate && !hasContent) return PptxSlideRoleEnd;
    if (slideIndex == 1 && hasTitle) return PptxSlideRoleCover;
    if (slideIndex == totalSlides) return PptxSlideRoleEnd;
    return PptxSlideRoleUnknown;
}

static NSInteger PptxMaxRelationshipId(NSString *relsXml) {
    NSRegularExpression *re = [NSRegularExpression regularExpressionWithPattern:@"Id=\"rId(\\d+)\"" options:0 error:nil];
    __block NSInteger max = 0;
    [re enumerateMatchesInString:relsXml options:0 range:NSMakeRange(0, relsXml.length) usingBlock:^(NSTextCheckingResult *result, NSMatchingFlags flags, BOOL *stop) {
        max = MAX(max, [[relsXml substringWithRange:[result rangeAtIndex:1]] integerValue]);
    }];
    return max;
}

static NSInteger PptxMaxSldId(NSString *presXml) {
    NSRegularExpression *re = [NSRegularExpression regularExpressionWithPattern:@"<(?:p:)?sldId id=\"(\\d+)\"" options:0 error:nil];
    __block NSInteger max = 255;
    [re enumerateMatchesInString:presXml options:0 range:NSMakeRange(0, presXml.length) usingBlock:^(NSTextCheckingResult *result, NSMatchingFlags flags, BOOL *stop) {
        max = MAX(max, [[presXml substringWithRange:[result rangeAtIndex:1]] integerValue]);
    }];
    return max;
}

static NSInteger PptxCountPoints(NSDictionary *slide) {
    id points = slide[@"content_points"];
    if (![points isKindOfClass:[NSArray class]]) {
        return 0;
    }
    NSInteger count = 0;
    for (id item in (NSArray *)points) {
        if ([item isKindOfClass:[NSString class]] && [(NSString *)item stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]].length > 0) {
            count++;
        }
    }
    return count;
}

static NSString *PptxToday(void) {
    NSDateFormatter *fmt = [[NSDateFormatter alloc] init];
    fmt.dateFormat = @"yyyy-MM-dd";
    return [fmt stringFromDate:[NSDate date]];
}

static NSString *PptxString(id value) {
    if ([value isKindOfClass:[NSString class]]) {
        return value;
    }
    if ([value respondsToSelector:@selector(stringValue)]) {
        return [value stringValue];
    }
    return @"";
}

@interface PptxPlannedSlide : NSObject
@property (assign, nonatomic) NSInteger sourceIndex;
@property (copy, nonatomic) NSDictionary<NSString *, NSString *> *placeholders;
@end

@implementation PptxPlannedSlide
@end

@implementation PptxTemplateService

+ (NSArray<PptxTemplateInfo *> *)loadIndex {
    NSURL *root = PptxTemplatesRoot();
    if (!root) {
        return @[];
    }
    NSURL *indexURL = [root URLByAppendingPathComponent:@"index.json"];
    NSData *data = [NSData dataWithContentsOfURL:indexURL];
    if (!data) {
        return @[];
    }
    id json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (![json isKindOfClass:[NSDictionary class]]) {
        return @[];
    }
    NSArray *raw = json[@"templates"];
    if (![raw isKindOfClass:[NSArray class]]) {
        return @[];
    }
    NSMutableArray<PptxTemplateInfo *> *result = [NSMutableArray array];
    for (id item in raw) {
        if (![item isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        NSDictionary *obj = item;
        PptxTemplateInfo *info = [[PptxTemplateInfo alloc] init];
        info.templateId = PptxString(obj[@"id"]);
        info.name = PptxString(obj[@"name"]);
        info.templateDescription = PptxString(obj[@"description"]);
        info.coverImage = PptxString(obj[@"coverImage"]);
        info.file = PptxString(obj[@"file"]);
        info.slideCount = [obj[@"slideCount"] integerValue];
        info.maxPoints = [obj[@"maxPoints"] integerValue];
        NSMutableArray<NSNumber *> *variants = [NSMutableArray array];
        if ([obj[@"variants"] isKindOfClass:[NSArray class]]) {
            for (id v in obj[@"variants"]) {
                [variants addObject:@([v integerValue])];
            }
        }
        info.variants = variants;
        if (info.templateId.length > 0) {
            [result addObject:info];
        }
    }
    return result;
}

+ (PptxTemplateInfo *)templateWithId:(NSString *)templateId {
    for (PptxTemplateInfo *info in [self loadIndex]) {
        if ([info.templateId isEqualToString:templateId]) {
            return info;
        }
    }
    return nil;
}

+ (UIImage *)coverImageForTemplate:(PptxTemplateInfo *)info {
    NSURL *root = PptxTemplatesRoot();
    if (!root || info.coverImage.length == 0) {
        return nil;
    }
    NSURL *url = [root URLByAppendingPathComponent:info.coverImage];
    NSData *data = [NSData dataWithContentsOfURL:url];
    if (!data) {
        return nil;
    }
    return [UIImage imageWithData:data];
}

+ (NSInteger)findSuitableSlide:(PptxTemplateInfo *)info pointCount:(NSInteger)pointCount {
    NSArray<NSNumber *> *variants = info.variants;
    for (NSNumber *v in variants) {
        if (v.integerValue == pointCount) {
            return pointCount;
        }
    }
    NSInteger best = -1;
    for (NSNumber *v in variants) {
        if (v.integerValue >= pointCount && (best == -1 || v.integerValue < best)) {
            best = v.integerValue;
        }
    }
    return best;
}

+ (NSArray<PptxPlannedSlide *> *)buildPlanWithCatalog:(NSArray<NSDictionary *> *)catalog
                                                 info:(PptxTemplateInfo *)info
                                        outlineSlides:(NSArray *)outlineSlides
                                    generatedByIndex:(NSDictionary<NSNumber *, NSDictionary *> *)generatedByIndex {
    NSMutableArray<PptxPlannedSlide *> *plan = [NSMutableArray array];
    NSString *today = PptxToday();
    NSInteger chapterNumber = 0;
    NSMutableDictionary<NSNumber *, NSNumber *> *variantUse = [NSMutableDictionary dictionary];

    NSInteger (^indexForRole)(PptxSlideRole) = ^NSInteger(PptxSlideRole role) {
        for (NSDictionary *item in catalog) {
            if ([item[@"role"] integerValue] == role) {
                return [item[@"index"] integerValue];
            }
        }
        if (role == PptxSlideRoleCover && catalog.count > 0) {
            return [catalog.firstObject[@"index"] integerValue];
        }
        if (role == PptxSlideRoleEnd && catalog.count > 0) {
            return [catalog.lastObject[@"index"] integerValue];
        }
        return 1;
    };
    NSInteger (^contentIndexForSlots)(NSInteger, BOOL) = ^NSInteger(NSInteger slotCount, BOOL consume) {
        NSMutableArray<NSNumber *> *matches = [NSMutableArray array];
        for (NSDictionary *item in catalog) {
            if ([item[@"role"] integerValue] == PptxSlideRoleContent && [item[@"points"] integerValue] == slotCount) {
                [matches addObject:item[@"index"]];
            }
        }
        if (matches.count == 0) {
            return -1;
        }
        NSInteger use = variantUse[@(slotCount)].integerValue;
        NSInteger idx = matches[use % matches.count].integerValue;
        if (consume) {
            variantUse[@(slotCount)] = @(use + 1);
        }
        return idx;
    };
    NSInteger (^bestSlots)(NSInteger) = ^NSInteger(NSInteger pointCount) {
        NSInteger exact = contentIndexForSlots(pointCount, NO);
        if (exact > 0) {
            return pointCount;
        }
        NSInteger variant = [self findSuitableSlide:info pointCount:pointCount];
        if (variant > 0 && contentIndexForSlots(variant, NO) > 0) {
            return variant;
        }
        NSInteger best = -1;
        for (NSDictionary *item in catalog) {
            if ([item[@"role"] integerValue] != PptxSlideRoleContent) {
                continue;
            }
            NSInteger points = [item[@"points"] integerValue];
            if (points <= pointCount && points > best) {
                best = points;
            }
        }
        return best > 0 ? best : 2;
    };

    for (NSInteger i = 0; i < (NSInteger)outlineSlides.count; i++) {
        id raw = outlineSlides[i];
        if (![raw isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        NSDictionary *outlineSlide = raw;
        NSString *type = PptxString(outlineSlide[@"type"]);
        if (type.length == 0) {
            type = @"section";
        }
        NSDictionary *generated = generatedByIndex[@(i)];
        if (!generated && [type isEqualToString:@"section_divider"]) {
            generated = outlineSlide;
        }
        PptxPlannedSlide *planned = [[PptxPlannedSlide alloc] init];
        if ([type isEqualToString:@"section_divider"]) {
            chapterNumber++;
            planned.sourceIndex = indexForRole(PptxSlideRoleSectionDivider);
            planned.placeholders = @{
                @"section_title": PptxString(outlineSlide[@"title"]),
                @"section_number": [NSString stringWithFormat:@"第%ld章", (long)chapterNumber],
                @"section_overview": PptxString(outlineSlide[@"content"]),
                @"title": PptxString(outlineSlide[@"title"]),
            };
            [plan addObject:planned];
            continue;
        }
        if (![generated isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        if ([type isEqualToString:@"cover"]) {
            NSString *title = PptxString(generated[@"title"]);
            NSString *subtitle = PptxString(generated[@"subtitle"]);
            planned.sourceIndex = indexForRole(PptxSlideRoleCover);
            planned.placeholders = @{
                @"title": title,
                @"subtitle": subtitle,
                @"author": subtitle.length > 0 ? subtitle : @"AI Office",
                @"date": today,
                @"cover_title": title,
            };
        } else if ([type isEqualToString:@"toc"]) {
            NSString *toc = PptxString(generated[@"content"]);
            if (toc.length == 0) {
                toc = PptxString(outlineSlide[@"content"]);
            }
            if (toc.length == 0 && [generated[@"content_points"] isKindOfClass:[NSArray class]]) {
                toc = [(NSArray *)generated[@"content_points"] componentsJoinedByString:@"\n"];
            }
            planned.sourceIndex = indexForRole(PptxSlideRoleToc);
            planned.placeholders = @{
                @"toc_content": toc,
                @"title": PptxString(generated[@"title"]).length > 0 ? PptxString(generated[@"title"]) : PptxString(outlineSlide[@"title"]),
            };
        } else if ([type isEqualToString:@"end"]) {
            NSString *title = PptxString(generated[@"title"]);
            if (title.length == 0) {
                title = @"谢谢";
            }
            NSString *subtitle = PptxString(generated[@"subtitle"]);
            NSString *contact = subtitle;
            if (contact.length == 0 && [generated[@"content_points"] isKindOfClass:[NSArray class]]) {
                NSArray *points = generated[@"content_points"];
                if (points.count > 0) {
                    contact = PptxString(points.firstObject);
                }
            }
            planned.sourceIndex = indexForRole(PptxSlideRoleEnd);
            planned.placeholders = @{
                @"title": title,
                @"end_title": title,
                @"author": subtitle.length > 0 ? subtitle : @"AI Office",
                @"date": today,
                @"contact_info": contact,
            };
        } else {
            NSInteger pointCount = PptxCountPoints(generated);
            NSInteger slotCount = bestSlots(pointCount);
            NSMutableDictionary<NSString *, NSString *> *ph = [NSMutableDictionary dictionary];
            NSString *title = PptxString(generated[@"title"]);
            ph[@"title"] = title;
            ph[@"subtitle"] = PptxString(generated[@"subtitle"]);
            ph[@"section_title"] = title;
            NSArray *contentPoints = [generated[@"content_points"] isKindOfClass:[NSArray class]] ? generated[@"content_points"] : @[];
            NSArray *detailed = [generated[@"detailed_content"] isKindOfClass:[NSArray class]] ? generated[@"detailed_content"] : @[];
            if (slotCount <= 0) {
                slotCount = MAX(pointCount, 2);
            }
            NSMutableArray *cpText = [NSMutableArray array];
            NSMutableArray *dcText = [NSMutableArray array];
            for (NSInteger p = 0; p < slotCount; p++) {
                NSString *cp = p < (NSInteger)contentPoints.count ? PptxString(contentPoints[p]) : @"";
                NSString *dc = p < (NSInteger)detailed.count ? PptxString(detailed[p]) : @"";
                ph[[NSString stringWithFormat:@"content_points[%ld]", (long)p]] = cp;
                ph[[NSString stringWithFormat:@"detailed_content[%ld]", (long)p]] = dc;
                [cpText addObject:cp];
                [dcText addObject:dc];
            }
            ph[@"content_points"] = [cpText componentsJoinedByString:@"\n"];
            ph[@"detailed_content"] = [dcText componentsJoinedByString:@"\n"];
            NSInteger srcIdx = contentIndexForSlots(slotCount, YES);
            if (srcIdx <= 0) {
                srcIdx = indexForRole(PptxSlideRoleContent);
            }
            planned.sourceIndex = srcIdx;
            planned.placeholders = ph;
        }
        [plan addObject:planned];
    }
    return plan;
}

+ (NSURL *)fillAndAssembleTemplateId:(NSString *)templateId
                       outlineSlides:(NSArray *)outlineSlides
                   generatedByIndex:(NSDictionary<NSNumber *, NSDictionary *> *)generatedByIndex
                         outputName:(NSString *)outputName
                              error:(NSError **)error {
    PptxTemplateInfo *info = [self templateWithId:templateId];
    NSURL *root = PptxTemplatesRoot();
    if (!info || !root) {
        if (error) {
            *error = [NSError errorWithDomain:kPptxErrorDomain code:1 userInfo:@{NSLocalizedDescriptionKey: @"模板不存在"}];
        }
        return nil;
    }
    NSURL *templateURL = [root URLByAppendingPathComponent:info.file];
    NSData *zipData = [NSData dataWithContentsOfURL:templateURL];
    if (!zipData) {
        if (error) {
            *error = [NSError errorWithDomain:kPptxErrorDomain code:2 userInfo:@{NSLocalizedDescriptionKey: @"无法读取模板文件"}];
        }
        return nil;
    }
    NSMutableDictionary<NSString *, NSData *> *entries = [NSMutableDictionary dictionary];
    if (!PptxUnzipEntries(zipData, entries)) {
        if (error) {
            *error = [NSError errorWithDomain:kPptxErrorDomain code:3 userInfo:@{NSLocalizedDescriptionKey: @"模板解压失败"}];
        }
        return nil;
    }

    NSMutableArray<NSNumber *> *slideIndices = [NSMutableArray array];
    for (NSString *key in entries) {
        if ([key hasPrefix:@"ppt/slides/slide"] && [key hasSuffix:@".xml"] && ![key containsString:@"_rels"]) {
            NSString *num = [key substringWithRange:NSMakeRange(@"ppt/slides/slide".length, key.length - @"ppt/slides/slide".length - 4)];
            [slideIndices addObject:@([num integerValue])];
        }
    }
    [slideIndices sortUsingSelector:@selector(compare:)];
    NSMutableArray<NSDictionary *> *catalog = [NSMutableArray array];
    for (NSNumber *idx in slideIndices) {
        NSData *slideBytes = entries[[NSString stringWithFormat:@"ppt/slides/slide%@.xml", idx]];
        if (!slideBytes) {
            continue;
        }
        NSSet<NSString *> *keys = PptxExtractPlaceholderKeys(slideBytes);
        NSInteger pointCount = PptxCountContentPointSlots(keys);
        PptxSlideRole role = PptxClassifyRole(keys, pointCount, idx.integerValue, (NSInteger)slideIndices.count);
        [catalog addObject:@{ @"index": idx, @"role": @(role), @"points": @(pointCount) }];
    }

    NSArray<PptxPlannedSlide *> *plan = [self buildPlanWithCatalog:catalog
                                                              info:info
                                                     outlineSlides:outlineSlides
                                                 generatedByIndex:generatedByIndex];
    if (plan.count == 0) {
        if (error) {
            *error = [NSError errorWithDomain:kPptxErrorDomain code:4 userInfo:@{NSLocalizedDescriptionKey: @"幻灯片计划为空"}];
        }
        return nil;
    }

    NSMutableArray<NSData *> *outputSlides = [NSMutableArray array];
    NSMutableArray<NSData *> *outputRels = [NSMutableArray array];
    for (PptxPlannedSlide *planned in plan) {
        NSString *srcName = [NSString stringWithFormat:@"ppt/slides/slide%ld.xml", (long)planned.sourceIndex];
        NSData *srcBytes = entries[srcName];
        if (!srcBytes) {
            continue;
        }
        [outputSlides addObject:PptxFillSlideBytes(srcBytes, planned.placeholders)];
        NSData *rels = entries[[NSString stringWithFormat:@"ppt/slides/_rels/slide%ld.xml.rels", (long)planned.sourceIndex]];
        [outputRels addObject:rels ?: [NSData data]];
    }
    NSInteger outputCount = (NSInteger)outputSlides.count;
    if (outputCount == 0) {
        if (error) {
            *error = [NSError errorWithDomain:kPptxErrorDomain code:5 userInfo:@{NSLocalizedDescriptionKey: @"没有可写入的幻灯片"}];
        }
        return nil;
    }

    NSArray<NSString *> *allKeys = [entries allKeys];
    for (NSString *key in allKeys) {
        BOOL isSlide = ([key hasPrefix:@"ppt/slides/slide"] && [key hasSuffix:@".xml"])
            || ([key hasPrefix:@"ppt/slides/_rels/slide"] && [key hasSuffix:@".xml.rels"]);
        if (isSlide) {
            [entries removeObjectForKey:key];
        }
    }
    for (NSInteger i = 0; i < outputCount; i++) {
        entries[[NSString stringWithFormat:@"ppt/slides/slide%ld.xml", (long)(i + 1)]] = outputSlides[i];
        entries[[NSString stringWithFormat:@"ppt/slides/_rels/slide%ld.xml.rels", (long)(i + 1)]] = outputRels[i];
    }

    NSData *relsBytes = entries[@"ppt/_rels/presentation.xml.rels"];
    NSData *presBytes = entries[@"ppt/presentation.xml"];
    if (relsBytes && presBytes) {
        NSString *relsXml = [[NSString alloc] initWithData:relsBytes encoding:NSUTF8StringEncoding] ?: @"";
        NSRegularExpression *slideRel = [NSRegularExpression regularExpressionWithPattern:@"<Relationship\\s[^>]*Type=\"http://schemas\\.openxmlformats\\.org/officeDocument/2006/relationships/slide\"[^>]*/>\\s*"
                                                                                  options:0
                                                                                    error:nil];
        relsXml = [slideRel stringByReplacingMatchesInString:relsXml options:0 range:NSMakeRange(0, relsXml.length) withTemplate:@""];
        NSInteger nextRId = PptxMaxRelationshipId(relsXml) + 1;
        NSMutableArray<NSString *> *slideRIds = [NSMutableArray array];
        NSMutableString *additions = [NSMutableString string];
        for (NSInteger i = 0; i < outputCount; i++) {
            NSString *rId = [NSString stringWithFormat:@"rId%ld", (long)nextRId];
            nextRId++;
            [slideRIds addObject:rId];
            [additions appendFormat:@"<Relationship Id=\"%@\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide%ld.xml\"/>", rId, (long)(i + 1)];
        }
        NSRange insert = [relsXml rangeOfString:@"</Relationships>" options:NSBackwardsSearch];
        if (insert.location != NSNotFound) {
            relsXml = [relsXml stringByReplacingCharactersInRange:insert withString:[additions stringByAppendingString:@"</Relationships>"]];
            entries[@"ppt/_rels/presentation.xml.rels"] = [relsXml dataUsingEncoding:NSUTF8StringEncoding];
        }
        NSString *presXml = [[NSString alloc] initWithData:presBytes encoding:NSUTF8StringEncoding] ?: @"";
        NSInteger nextSldId = PptxMaxSldId(presXml) + 1;
        NSMutableString *inner = [NSMutableString string];
        for (NSInteger i = 0; i < (NSInteger)slideRIds.count; i++) {
            [inner appendFormat:@"<p:sldId id=\"%ld\" r:id=\"%@\"/>", (long)(nextSldId + i), slideRIds[i]];
        }
        NSRegularExpression *sldList = [NSRegularExpression regularExpressionWithPattern:@"(<(?:p:)?sldIdLst>)(.*?)(</(?:p:)?sldIdLst>)"
                                                                                 options:NSRegularExpressionDotMatchesLineSeparators
                                                                                   error:nil];
        NSTextCheckingResult *listMatch = [sldList firstMatchInString:presXml options:0 range:NSMakeRange(0, presXml.length)];
        if (listMatch && listMatch.numberOfRanges >= 4) {
            NSString *open = [presXml substringWithRange:[listMatch rangeAtIndex:1]];
            NSString *close = [presXml substringWithRange:[listMatch rangeAtIndex:3]];
            presXml = [presXml stringByReplacingCharactersInRange:listMatch.range withString:[NSString stringWithFormat:@"%@%@%@", open, inner, close]];
            entries[@"ppt/presentation.xml"] = [presXml dataUsingEncoding:NSUTF8StringEncoding];
        }
    }

    NSData *ctBytes = entries[@"[Content_Types].xml"];
    if (ctBytes) {
        NSString *xml = [[NSString alloc] initWithData:ctBytes encoding:NSUTF8StringEncoding] ?: @"";
        NSRegularExpression *overrideRe = [NSRegularExpression regularExpressionWithPattern:@"<Override\\s[^>]*PartName=\"/ppt/slides/(?:slide\\d+\\.xml|_rels/slide\\d+\\.xml\\.rels)\"[^>]*/>\\s*"
                                                                                    options:0
                                                                                      error:nil];
        xml = [overrideRe stringByReplacingMatchesInString:xml options:0 range:NSMakeRange(0, xml.length) withTemplate:@""];
        NSMutableString *additions = [NSMutableString string];
        for (NSInteger i = 1; i <= outputCount; i++) {
            [additions appendFormat:@"<Override PartName=\"/ppt/slides/slide%ld.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>", (long)i];
            [additions appendFormat:@"<Override PartName=\"/ppt/slides/_rels/slide%ld.xml.rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>", (long)i];
        }
        NSRange insert = [xml rangeOfString:@"</Types>" options:NSBackwardsSearch];
        if (insert.location != NSNotFound) {
            xml = [xml stringByReplacingCharactersInRange:insert withString:[additions stringByAppendingString:@"</Types>"]];
            entries[@"[Content_Types].xml"] = [xml dataUsingEncoding:NSUTF8StringEncoding];
        }
    }

    NSString *safe = outputName.length > 0 ? outputName : @"AI生成PPT";
    safe = [[safe componentsSeparatedByCharactersInSet:[NSCharacterSet characterSetWithCharactersInString:@"\\/:*?\"<>|"]] componentsJoinedByString:@""];
    if (safe.length > 40) {
        safe = [safe substringToIndex:40];
    }
    NSURL *documents = [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask] lastObject];
    NSURL *destination = [documents URLByAppendingPathComponent:[NSString stringWithFormat:@"%@.pptx", safe]];
    NSUInteger suffix = 2;
    while ([[NSFileManager defaultManager] fileExistsAtPath:destination.path]) {
        destination = [documents URLByAppendingPathComponent:[NSString stringWithFormat:@"%@ %lu.pptx", safe, (unsigned long)suffix]];
        suffix++;
    }
    if (!PptxWriteZip(entries, destination)) {
        if (error) {
            *error = [NSError errorWithDomain:kPptxErrorDomain code:6 userInfo:@{NSLocalizedDescriptionKey: @"写入 PPTX 失败"}];
        }
        return nil;
    }
    return destination;
}

@end
