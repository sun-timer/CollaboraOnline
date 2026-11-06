// -*- Mode: ObjC++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

#import "AISettingsDrawerIcons.h"

#import "AIModelConfigStore.h"
#import "Settings/AppIcons.h"

static UIColor *AIDrawerIconColor(NSString *hex) {
    if (hex.length == 7 && [hex hasPrefix:@"#"]) {
        unsigned r = 0, g = 0, b = 0;
        NSScanner *scanner = [NSScanner scannerWithString:[hex substringFromIndex:1]];
        unsigned value = 0;
        if ([scanner scanHexInt:&value]) {
            r = (value >> 16) & 0xFF;
            g = (value >> 8) & 0xFF;
            b = value & 0xFF;
            return [UIColor colorWithRed:r / 255.0 green:g / 255.0 blue:b / 255.0 alpha:1];
        }
    }
    if (hex.length == 9 && [hex hasPrefix:@"#"]) {
        unsigned a = 0, r = 0, g = 0, b = 0;
        NSScanner *scanner = [NSScanner scannerWithString:[hex substringFromIndex:1]];
        unsigned value = 0;
        if ([scanner scanHexInt:&value]) {
            a = (value >> 24) & 0xFF;
            r = (value >> 16) & 0xFF;
            g = (value >> 8) & 0xFF;
            b = value & 0xFF;
            return [UIColor colorWithRed:r / 255.0 green:g / 255.0 blue:b / 255.0 alpha:a / 255.0];
        }
    }
    return UIColor.blackColor;
}

static void AIDrawerStrokePath(UIBezierPath *path, UIColor *color, CGFloat lineWidth) {
    path.lineWidth = lineWidth;
    path.lineCapStyle = kCGLineCapRound;
    path.lineJoinStyle = kCGLineJoinRound;
    [color setStroke];
    [path stroke];
}

static UIImage *AIDrawerRender(NSString *cacheKey, CGFloat size, void (^draw)(CGRect rect, CGFloat scale)) {
    static NSCache<NSString *, UIImage *> *cache;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        cache = [[NSCache alloc] init];
    });
    NSString *key = [NSString stringWithFormat:@"%@_%.0f", cacheKey, size];
    UIImage *cached = [cache objectForKey:key];
    if (cached) {
        return cached;
    }
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(size, size)];
    UIImage *image = [renderer imageWithActions:^(UIGraphicsImageRendererContext *ctx) {
        CGRect rect = CGRectMake(0, 0, size, size);
        CGFloat scale = size / 24.0;
        draw(rect, scale);
    }];
    if (image) {
        [cache setObject:image forKey:key];
    }
    return image;
}

@implementation AISettingsDrawerIcons

+ (UIImage *)iconNamed:(NSString *)name size:(CGFloat)size {
    UIImage *shared = [AppIcons iconNamed:name size:size];
    if (shared != nil) {
        return shared;
    }

    if ([name isEqualToString:@"chevron-right"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            [path moveToPoint:CGPointMake(9.0 * scale, 7.0 * scale)];
            [path addLineToPoint:CGPointMake(15.0 * scale, 12.0 * scale)];
            [path addLineToPoint:CGPointMake(9.0 * scale, 17.0 * scale)];
            AIDrawerStrokePath(path, AIDrawerIconColor(@"#66000000"), 2.0 * scale);
        });
    }
    if ([name isEqualToString:@"chevron-up"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            [path moveToPoint:CGPointMake(6.0 * scale, 15.0 * scale)];
            [path addLineToPoint:CGPointMake(12.0 * scale, 9.0 * scale)];
            [path addLineToPoint:CGPointMake(18.0 * scale, 15.0 * scale)];
            AIDrawerStrokePath(path, AIDrawerIconColor(@"#66000000"), 2.0 * scale);
        });
    }
    if ([name isEqualToString:@"chevron-down"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            [path moveToPoint:CGPointMake(6.0 * scale, 9.0 * scale)];
            [path addLineToPoint:CGPointMake(12.0 * scale, 15.0 * scale)];
            [path addLineToPoint:CGPointMake(18.0 * scale, 9.0 * scale)];
            AIDrawerStrokePath(path, AIDrawerIconColor(@"#66000000"), 2.0 * scale);
        });
    }
    if ([name isEqualToString:@"config-back"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIBezierPath *path = [UIBezierPath bezierPath];
            [path moveToPoint:CGPointMake(14.5 * scale, 7.0 * scale)];
            [path addLineToPoint:CGPointMake(8.5 * scale, 12.0 * scale)];
            [path addLineToPoint:CGPointMake(14.5 * scale, 17.0 * scale)];
            AIDrawerStrokePath(path, AIDrawerIconColor(@"#e6000000"), 2.0 * scale);
        });
    }
    if ([name isEqualToString:@"profile-edit"]) {
        // 设计 417:22741 的原始矢量（plumb_assets 导出），14pt、描边 2 design px
        UIImage *compose = [UIImage imageNamed:@"DrawerCompose"];
        if (compose != nil) {
            return [compose imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
        }
        return nil;
    }
    if ([name isEqualToString:@"model-vision"]) {
        // 设计 417:22741：方框（取景框）是正方形，四角 5.5 单位、线宽 2 单位，内部一只眼睛
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIColor *gray = AIDrawerIconColor(@"#6a6a6a");
            void (^corner)(CGFloat, CGFloat, CGFloat, CGFloat) = ^(CGFloat x1, CGFloat y1, CGFloat x2, CGFloat y2) {
                UIBezierPath *path = [UIBezierPath bezierPath];
                [path moveToPoint:CGPointMake(x1 * scale, y1 * scale)];
                [path addLineToPoint:CGPointMake(x2 * scale, y2 * scale)];
                AIDrawerStrokePath(path, gray, 2.0 * scale);
            };
            corner(10.0, 3.0, 3.0, 3.0);
            corner(3.0, 3.0, 3.0, 10.0);
            corner(14.0, 3.0, 21.0, 3.0);
            corner(21.0, 3.0, 21.0, 10.0);
            corner(10.0, 21.0, 3.0, 21.0);
            corner(3.0, 21.0, 3.0, 14.0);
            corner(14.0, 21.0, 21.0, 21.0);
            corner(21.0, 21.0, 21.0, 14.0);
            UIBezierPath *eye = [UIBezierPath bezierPath];
            [eye moveToPoint:CGPointMake(5.6 * scale, 12.0 * scale)];
            [eye addQuadCurveToPoint:CGPointMake(18.4 * scale, 12.0 * scale)
                        controlPoint:CGPointMake(12.0 * scale, 16.6 * scale)];
            [eye addQuadCurveToPoint:CGPointMake(5.6 * scale, 12.0 * scale)
                        controlPoint:CGPointMake(12.0 * scale, 7.4 * scale)];
            AIDrawerStrokePath(eye, gray, 2.0 * scale);
            UIBezierPath *pupil = [UIBezierPath bezierPathWithArcCenter:CGPointMake(12.0 * scale, 12.0 * scale)
                                                                 radius:2.0 * scale
                                                             startAngle:0
                                                               endAngle:(CGFloat)(2 * M_PI)
                                                              clockwise:YES];
            [gray setFill];
            [pupil fill];
        });
    }
    if ([name isEqualToString:@"model-local-inference"]) {
        return AIDrawerRender(name, size, ^(CGRect rect, CGFloat scale) {
            UIColor *gray = AIDrawerIconColor(@"#6a6a6a");
            CGFloat factor = scale / 2.0;
            UIBezierPath *(^circle)(CGFloat, CGFloat, CGFloat) = ^UIBezierPath *(CGFloat x, CGFloat y, CGFloat radius) {
                return [UIBezierPath bezierPathWithOvalInRect:CGRectMake((x - radius) * factor,
                                                                        (y - radius) * factor,
                                                                        radius * 2.0 * factor,
                                                                        radius * 2.0 * factor)];
            };

            AIDrawerStrokePath(circle(23.9265, 10.8415, 4.2683), gray, 2.6 * factor);
            [gray setFill];
            [circle(23.9265, 24.5002, 4.2683) fill];

            UIBezierPath *links = [UIBezierPath bezierPath];
            [links moveToPoint:CGPointMake(36.7313 * factor, 16.8171 * factor)];
            [links addLineToPoint:CGPointMake(13.0 * factor, 32.3774 * factor)];
            [links moveToPoint:CGPointMake(13.2637 * factor, 19.2804 * factor)];
            [links addLineToPoint:CGPointMake(31.7472 * factor, 28.5632 * factor)];
            [links moveToPoint:CGPointMake(23.9277 * factor, 15.1099 * factor)];
            [links addLineToPoint:CGPointMake(23.9277 * factor, 39.0123 * factor)];
            AIDrawerStrokePath(links, gray, 2.0 * factor);

            AIDrawerStrokePath(circle(10.2683, 33.7683, 4.2683), gray, 2.6 * factor);
            AIDrawerStrokePath(circle(36.2763, 30.7759, 4.2683), gray, 2.6 * factor);
            [circle(38.0, 16.5, 4.0) fill];
            [circle(23.9258, 39.0122, 4.0) fill];
            [circle(13.2644, 19.2803, 4.0) fill];
        });
    }
    return nil;
}

+ (NSString *)modelIconNameForType:(NSInteger)modelType {
    switch ((AIModelType)modelType) {
        case AIModelTypeThink:
            return @"model-think";
        case AIModelTypeImage:
            return @"model-image";
        case AIModelTypeVision:
            return @"model-vision";
        case AIModelTypeBase:
        default:
            return @"model-base";
    }
}

@end
