#
# Copyright (c) 2019 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at:
#
#     https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#   Red Hat, Inc. - initial API and implementation
#

FROM openjdk:11.0.16-jre-slim
COPY maven/target/*.jar /deployments/app.jar
COPY maven/static-dir-in-project-root/my-file.txt /deployments/my-file.txt
EXPOSE 8080
CMD ["java", "-jar", "/deployments/app.jar"]
