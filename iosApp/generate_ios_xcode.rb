#!/usr/bin/env ruby
# frozen_string_literal: true

# 生成 KuiklyStockChat.xcodeproj（iosApp 内）
# 用法：cd iosApp && ruby generate_ios_xcode.rb

require 'xcodeproj'

project_path = File.expand_path('KuiklyStockChat.xcodeproj', __dir__)
project = Xcodeproj::Project.new(project_path)

# Project-level Build Settings
project.build_configurations.each do |config|
  config.build_settings.merge!(
    'IPHONEOS_DEPLOYMENT_TARGET' => '14.1',
    'SWIFT_VERSION' => '5.0',
    'CLANG_ENABLE_OBJC_ARC' => 'YES',
    'CLANG_ENABLE_MODULES' => 'YES',
    'GCC_C_LANGUAGE_STANDARD' => 'gnu11',
    'CLANG_CXX_LANGUAGE_STANDARD' => 'gnu++17',
    'ENABLE_BITCODE' => 'NO',
    'CODE_SIGN_STYLE' => 'Automatic',
    'CODE_SIGNING_REQUIRED' => 'NO',
    'CODE_SIGNING_ALLOWED' => 'NO',
    'PRODUCT_NAME' => 'KuiklyStockChat',
    'MARKETING_VERSION' => '1.0.0',
    'CURRENT_PROJECT_VERSION' => '1',
    'TARGETED_DEVICE_FAMILY' => '1,2',
    'CLANG_ALLOW_NON_MODULAR_INCLUDES_IN_FRAMEWORK_MODULES' => 'YES',
    'GCC_PREPROCESSOR_DEFINITIONS' => ['DEBUG=1', '$(inherited)'],
  )
end

# App target
target = project.new_target(:application, 'KuiklyStockChat', :ios, '14.1')
target.build_configurations.each do |config|
  config.build_settings.merge!(
    'INFOPLIST_FILE' => 'KuiklyStockChat/Info.plist',
    'PRODUCT_BUNDLE_IDENTIFIER' => 'com.kuikly.stockchat',
    'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC'],
  )
end

# 1. 创建 KuiklyStockChat 子 group（物理路径相对 project = iosApp/KuiklyStockChat）
main_group = project.main_group
kuikly_group = main_group.new_group('KuiklyStockChat', 'KuiklyStockChat')

# 2. 递归加入文件
src_dir = File.expand_path('KuiklyStockChat', __dir__)
sources_phase = target.source_build_phase
resources_phase = target.resources_build_phase

# 手动按相对路径扫描，但不需要递归目录结构 — xcodeproj 会自动通过 PBXFileReference 处理
Dir.chdir(src_dir) do
  Pathname.glob('**/*').each do |entry|
    next if entry.directory?
    rel = entry.to_s

    ref = kuikly_group.new_reference(rel)
    # file type 由扩展名自动判断，下面 4 类手动设置（更稳）
    if rel.end_with?('.m') || rel.end_with?('.mm') || rel.end_with?('.swift')
      ref.set_last_known_file_type('sourcecode.c.objc') if rel.end_with?('.m')
      ref.set_last_known_file_type('sourcecode.swift') if rel.end_with?('.swift')
      sources_phase.add_file_reference(ref)
    elsif rel.end_with?('.h')
      ref.set_last_known_file_type('sourcecode.c.h')
      # 头文件不放 build phase
    elsif rel.end_with?('.storyboard')
      ref.set_last_known_file_type('file.storyboard')
      resources_phase.add_file_reference(ref)
    elsif rel.end_with?('.xcassets')
      ref.set_last_known_file_type('folder.assetcatalog')
      resources_phase.add_file_reference(ref)
    end
  end
end

project.save
puts "✅ Generated #{project_path}"
