/*
 * Copyright (c) 2020 linqu.tech, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tech.linqu.webpb.ts;

import org.apache.commons.lang3.StringUtils;
import tech.linqu.webpb.ts.generator.Generator;
import tech.linqu.webpb.utilities.context.RequestContext;
import tech.linqu.webpb.utilities.utils.WebpbExtend;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;

import java.util.ArrayList;

public class Main {

    public static void main(String[] args) throws Exception {
        RequestContext context = new RequestContext(WebpbExtend.FileOpts::hasTs);

        for (FileDescriptor fileDescriptor : context.getTargetDescriptors()) {
            if (shouldIgnore(fileDescriptor.getPackage())) {
                continue;
            }
            StringBuilder sb = Generator
                .of(context, fileDescriptor, new ArrayList<>())
                .generate();
            if (sb.length() == 0) {
                continue;
            }

            CodeGeneratorResponse.Builder builder = CodeGeneratorResponse.newBuilder();
            builder.addFileBuilder()
                .setName(fileDescriptor.getPackage() + ".ts")
                .setContent(sb.toString());
            CodeGeneratorResponse response = builder.build();
            response.writeTo(System.out);
        }
    }

    private static boolean shouldIgnore(String packageName) {
        return StringUtils.isEmpty(packageName) || "google.protobuf".equals(packageName);
    }
}
