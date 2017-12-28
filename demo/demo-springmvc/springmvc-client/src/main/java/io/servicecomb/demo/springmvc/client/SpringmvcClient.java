/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.demo.springmvc.client;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import io.servicecomb.core.CseContext;
import io.servicecomb.demo.DemoConst;
import io.servicecomb.demo.TestMgr;
import io.servicecomb.demo.controller.Controller;
import io.servicecomb.demo.controller.Person;
import io.servicecomb.foundation.common.utils.BeanUtils;
import io.servicecomb.foundation.common.utils.JsonUtils;
import io.servicecomb.foundation.common.utils.Log4jUtils;
import io.servicecomb.metrics.common.MetricsPublisher;
import io.servicecomb.metrics.common.RegistryMetric;
import io.servicecomb.provider.springmvc.reference.CseRestTemplate;
import io.servicecomb.provider.springmvc.reference.RestTemplateBuilder;
import io.servicecomb.provider.springmvc.reference.UrlWithServiceNameClientHttpRequestFactory;

public class SpringmvcClient {
  private static RestTemplate templateUrlWithServiceName = new CseRestTemplate();

  private static RestTemplate restTemplate;

  private static Controller controller;

  private static MetricsPublisher metricsPublisher;

  public static void main(String[] args) throws Exception {
    templateUrlWithServiceName.setRequestFactory(new UrlWithServiceNameClientHttpRequestFactory());
    Log4jUtils.init();
    BeanUtils.init();

    run();

    TestMgr.summary();
  }

  public static void run() throws Exception {
    restTemplate = RestTemplateBuilder.create();
    controller = BeanUtils.getBean("controller");
    metricsPublisher = BeanUtils.getBean("metricsPublisher");


    String prefix = "cse://springmvc";

    try {
      // this test class is intended for retry hanging issue JAV-127
      templateUrlWithServiceName.getForObject(prefix + "/controller/sayhi?name=throwexception", String.class);
      TestMgr.check("true", "false");
    } catch (Exception e) {
      TestMgr.check("true", "true");
    }

    CodeFirstRestTemplateSpringmvc codeFirstClient =
        BeanUtils.getContext().getBean(CodeFirstRestTemplateSpringmvc.class);
    codeFirstClient.testCodeFirst(restTemplate, "springmvc", "/codeFirstSpringmvc/");

    String microserviceName = "springmvc";
    for (String transport : DemoConst.transports) {
      CseContext.getInstance().getConsumerProviderManager().setTransport(microserviceName, transport);
      TestMgr.setMsg(microserviceName, transport);

      testController(templateUrlWithServiceName, microserviceName);

      testController();
    }

    //0.5.0 version metrics integration test
    try {
      // this test class is intended for retry hanging issue JAV-127
      String content = restTemplate.getForObject("cse://springmvc/codeFirstSpringmvc/metricsForTest", String.class);
      Map<String, String> resultMap = JsonUtils.OBJ_MAPPER.readValue(content, HashMap.class);

      TestMgr.check(String.valueOf(true), String.valueOf(resultMap.get("CPU and Memory").contains("heapUsed=")));

      TestMgr.check(resultMap.get("totalRequestProvider OPERATIONAL_LEVEL"),
          "{springmvc.codeFirst.saySomething=3, springmvc.codeFirst.testRawJsonAnnotation=3, " +
              "springmvc.codeFirst.sayHi2=3, springmvc.codeFirst.responseEntity=6, springmvc.codeFirst.fileUpload=3, " +
              "springmvc.codeFirst.responseEntityPATCH=3, springmvc.codeFirst.textPlain=3, " +
              "springmvc.codeFirst.metricsForTest=1, springmvc.codeFirst.testform=6, " +
              "springmvc.controller.saySomething=6, springmvc.codeFirst.fallbackReturnNull=6, " +
              "springmvc.codeFirst.addString=3, springmvc.codeFirst.reduce=3, springmvc.codeFirst.sayHi=3, " +
              "springmvc.codeFirst.cseResponse=6, springmvc.codeFirst.bytes=3, springmvc.controller.sayHei=3, " +
              "springmvc.codeFirst.fallbackThrowException=9, springmvc.codeFirst.testModelWithIgnoreField=1, " +
              "springmvc.codeFirst.testUserMap=3, springmvc.codeFirst.isTrue=3, springmvc.codeFirst.add=3, " +
              "springmvc.codeFirst.fallbackFromCache=6, springmvc.controller.sayHi=17, springmvc.codeFirst.sayHello=6,"
              +
              " springmvc.controller.sayHello=6, springmvc.codeFirst.addDate=3}");

      TestMgr.check(String.valueOf(resultMap.get("RequestQueueRelated").contains("springmvc.codeFirst.saySomething")),
          String.valueOf(true));
      TestMgr.check(String.valueOf(resultMap.get("RequestQueueRelated").contains("springmvc.controller.sayHi")),
          String.valueOf(true));
    } catch (Exception e) {
      TestMgr.check("true", "false");
    }

    //0.5.0 later version metrics integration test
    try {
      RegistryMetric metric = metricsPublisher.metrics();

      TestMgr.check(String.valueOf(metric.getInstanceMetric().getSystemMetric().getHeapUsed() != 0), "true");
      TestMgr.check(String.valueOf(metric.getProducerMetrics().size() == 28), "true");
      TestMgr.check(String.valueOf(
          metric.getProducerMetrics().get("springmvc.codeFirst.saySomething").getProducerCall().getTotal() == 3),
          "true");
    } catch (Exception e) {
      TestMgr.check("true", "false");
    }
  }

  private static void testController(RestTemplate template, String microserviceName) {
    String prefix = "cse://" + microserviceName;

    TestMgr.check("hi world [world]",
        template.getForObject(prefix + "/controller/sayhi?name=world",
            String.class));

    TestMgr.check("hi world1 [world1]",
        template.getForObject(prefix + "/controller/sayhi?name={name}",
            String.class,
            "world1"));
    TestMgr.check("hi hi 中国 [hi 中国]",
        template.getForObject(prefix + "/controller/sayhi?name={name}",
            String.class,
            "hi 中国"));

    Map<String, String> params = new HashMap<>();
    params.put("name", "world2");
    TestMgr.check("hi world2 [world2]",
        template.getForObject(prefix + "/controller/sayhi?name={name}",
            String.class,
            params));

    TestMgr.check("hello world",
        template.postForObject(prefix + "/controller/sayhello/{name}",
            null,
            String.class,
            "world"));
    TestMgr.check("hello hello 中国",
        template.postForObject(prefix + "/controller/sayhello/{name}",
            null,
            String.class,
            "hello 中国"));

    HttpHeaders headers = new HttpHeaders();
    headers.add("name", "world");
    @SuppressWarnings("rawtypes")
    HttpEntity entity = new HttpEntity<>(null, headers);
    ResponseEntity<String> response = template.exchange(prefix + "/controller/sayhei",
        HttpMethod.GET,
        entity,
        String.class);
    TestMgr.check("hei world", response.getBody());

    Person user = new Person();
    user.setName("world");
    TestMgr.check("ha world",
        template.postForObject(prefix + "/controller/saysomething?prefix={prefix}",
            user,
            String.class,
            "ha"));
  }

  private static void testController() {
    TestMgr.check("hi world [world]", controller.sayHi("world"));
    Person user = new Person();
    user.setName("world");
    TestMgr.check("ha world", controller.saySomething("ha", user));
  }
}
