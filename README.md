# ![logo_image] Sketch Image Loader

![Platform][platform_image]
[![API][min_api_image]][min_api_link]
[![License][license_image]][license_link]
[![version_icon]][version_link]
![QQ Group][qq_group_image]

Sketch 是 Android 上的一个强大且全面的图片加载库，除了基础功能外，还支持 GIF、SVG，手势缩放、分块显示超大图片、ExifInterface、视频缩略图、Jetpack
Compose 等功能

## 关于 3.0 版本

* 3.0 版本全部用 kotlin 重写，并且 maven groupId 和包名已经变更所以与 2.0 版本完全不冲突，两者可以共存
* 3.0 版本参考 [coil][coil] 2.0.0-alpha05 版本并结合 sketch 原有功能实现，相较于 [coil][coil] sketch 最低支持到 API
  16，而 [coil][coil] 是 21

## 简介

* 支持 http、asset、content、drawable 等多种 URI
* 支持播放 gif、webp、heif 等动图
* 支持手势缩放及分块显示超大图片
* 支持下载、转换结果、内存三级缓存
* 支持通过 ExifInterface 纠正图片方向
* 支持 Base64、视频帧、SVG 图片
* 支持 Jetpack Compose
* 支持根据 view 大小自动调整图片尺寸
* 支持仅加载图片到内存或仅下载图片到磁盘
* 支持节省蜂窝流量等各种实用功能
* 支持对 URI、缓存、解码、转换、显示、占位图等各个环节的扩展
* 基于 Kotlin 及 Kotlin 协程编写

## 导入

`已发布到 mavenCentral`

```kotlin
dependencies {
    implementation("io.github.panpf.sketch3:sketch:${LAST_VERSION}")
}
```

`${LAST_VERSION}`: [![Download][version_icon]][version_link] (不包含 'v')

还有一些可选的模块用来扩展 sketch 的功能：

```kotlin
dependencies {
    // 支持 Jetpack Compose
    implementation("io.github.panpf.sketch3:sketch-compose:${LAST_VERSION}")

    // 支持下载进度蒙层、列表滑动中暂停加载、节省蜂窝流量、图片类型角标、加载 apk 文件和已安装 app 图标等实用功能
    implementation("io.github.panpf.sketch3:sketch-extensions:${LAST_VERSION}")

    // 通过 koral--/android-gif-drawable 库的 GifDrawable 实现 gif 播放
    implementation("io.github.panpf.sketch3:sketch-gif-koral:${LAST_VERSION}")

    // 通过 Android 内置的 Movie 类实现 gif 播放
    implementation("io.github.panpf.sketch3:sketch-gif-movie:${LAST_VERSION}")

    // 支持 OkHttp
    implementation("io.github.panpf.sketch3:sketch-okhttp:${LAST_VERSION}")

    // 支持 SVG 图片
    implementation("io.github.panpf.sketch-svg:${LAST_VERSION}")

    // 通过 Android 内置的 MediaMetadataRetriever 类实现读取视频帧 
    implementation("io.github.panpf.sketch-video:${LAST_VERSION}")

    // 通过 wseemann 的 FFmpegMediaMetadataRetriever 库实现读取视频帧
    implementation("io.github.panpf.sketch-video-ffmpeg:${LAST_VERSION}")

    // 支持手势缩放显示图片以及分块显示超大图片
    implementation("io.github.panpf.sketch3:sketch-zoom:${LAST_VERSION}")
}
```

#### R8 / Proguard

sketch 自己不需要配置任何混淆规则，但你可能需要为间接依赖的 [Kotlin Coroutines], [OkHttp], [Okio] 配置一些规则

## 快速上手

#### ImageView

```kotlin
// url
imageView.displayImage("https://www.sample.com/image.jpg")

// File
imageView.displayImage("/sdcard/download/image.jpeg")

// asset
imageView.displayImage("asset://image.jpg")

// There is a lot more...
```

还可以通过尾随的 lambda 函数配置参数：

```kotlin
imageView.displayImage("https://www.sample.com/image.jpg") {
    placeholder(R.drawable.placeholder)
    error(R.drawable.error)
    transformations(CircleCropTransformation())
    crossfade()
    // There is a lot more...
}
```

#### Jetpack Compose

需要先导入 `sketch-compose` 模块

```kotlin
AsyncImage(
    imageUri = "https://www.sample.com/image.jpg",
    modifier = Modifier.size(300.dp, 200.dp),
    contentScale = ContentScale.Crop,
    contentDescription = ""
) {
    placeholder(R.drawable.placeholder)
    error(R.drawable.error)
    transformations(CircleCropTransformation())
    crossfade()
    // There is a lot more...
}
```

## 文档

* [入门][getting_started]
* [Fetcher：了解 Fetcher 及扩展新的 URI 类型][fetcher]
* [Decoder：了解 Decoder 及扩展新的图片类型][decoder]
* [播放 gif、webp、heif 动图][animated_image]
* [Resize：修改图片尺寸][resize]
* [Transformation：转换图片][transformation]
* [Transition：用炫酷的过渡方式显示图片][transition]
* [StateImage：占位图和错误图][state_image]
* Listener：监听请求状态和下载进度
* Cache：配置下载、转换结果、内存三级缓存
* HttpStack：将 http 网络部分替换成 okhttp
* 解码 svg 图片
* 解码视频帧
* 预加载图片到内存
* 仅加载图片获取 Bitmap
* 仅下载图片到磁盘
* ExifInterface 纠正图片方向

特色小功能

* SketchImageView：显示下载进度、图片类型角标
* SketchZoomImageView：使用手势缩放及分块显示超大图功能
* 使用手势缩放功能的阅读模式提升体验
* 使用 resize 的长图裁剪功能提升超大图片在列表中的清晰度
* 蜂窝数据网络下暂停下载图片节省流量
* 列表滑动时暂停加载图片，提升列表滑动流畅度
* 显示 apk 文件或已安装 app 的图标
* 设置日志级别
* 通过 ImageOptions 统一配置参数

[comment]: <> (## 示例 APP)

[comment]: <> (![sample_app_download_qrcode])

[comment]: <> (扫描二维码下载或[点我下载][sample_app_download_link])

## 更新日志

请查看 [CHANGELOG.md] 文件

## 特别感谢

* [coil-kt]/[coil]: framework、compose
* [bumptech]/[glide]: BitmapPool
* [chrisbanes]/[PhotoView]: Zoom
* [koral--]/[android-gif-drawable]: gif-koral
* [wseemann]/[FFmpegMediaMetadataRetriever]: video-ffmpeg
* [BigBadaboom]/[androidsvg]: svg

## License

    Copyright (C) 2019 panpf <panpfpanpf@outlook.com>

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[comment]: <> (header)

[logo_image]: docs/res/logo.png

[platform_image]: https://img.shields.io/badge/Platform-Android-brightgreen.svg

[license_image]: https://img.shields.io/badge/License-Apache%202-blue.svg

[license_link]: https://www.apache.org/licenses/LICENSE-2.0

[version_icon]: https://img.shields.io/maven-central/v/io.github.panpf.sketch3/sketch

[version_link]: https://repo1.maven.org/maven2/io/github/panpf/sketch/

[min_api_image]: https://img.shields.io/badge/API-16%2B-orange.svg

[min_api_link]: https://android-arsenal.com/api?level=16

[qq_group_image]: https://img.shields.io/badge/QQ%E4%BA%A4%E6%B5%81%E7%BE%A4-529630740-red.svg


[comment]: <> (wiki)

[getting_started]: docs/wiki/getting_started.md

[fetcher]: docs/wiki/fetcher.md

[decoder]: docs/wiki/decoder.md

[animated_image]: docs/wiki/animated_image.md

[resize]: docs/wiki/resize.md

[transformation]: docs/wiki/transformation.md

[transition]: docs/wiki/transition.md

[state_image]: docs/wiki/state_image.md


[comment]: <> (links)

[koral--]: https://github.com/koral--

[android-gif-drawable]: https://github.com/koral--/android-gif-drawable

[chrisbanes]: https://github.com/chrisbanes

[PhotoView]: https://github.com/chrisbanes/PhotoView

[bumptech]: https://github.com/bumptech

[glide]: https://github.com/bumptech/glide

[coil-kt]: https://github.com/coil-kt

[coil]: https://github.com/coil-kt/coil

[wseemann]: https://github.com/wseemann

[FFmpegMediaMetadataRetriever]: https://github.com/wseemann/FFmpegMediaMetadataRetriever

[BigBadaboom]: https://github.com/BigBadaboom

[androidsvg]: https://github.com/BigBadaboom/androidsvg

[Kotlin Coroutines]: https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/resources/META-INF/proguard/coroutines.pro

[OkHttp]: https://github.com/square/okhttp/blob/master/okhttp/src/jvmMain/resources/META-INF/proguard/okhttp3.pro

[Okio]: https://github.com/square/okio/blob/master/okio/src/jvmMain/resources/META-INF/proguard/okio.pro


[comment]: <> (footer)

[CHANGELOG.md]: CHANGELOG.md

[sample_app_download_qrcode]: docs/sketch-sample.png

[sample_app_download_link]: https://github.com/panpf/sketch/raw/master/docs/sketch-sample.apk