title: LeetCode 两个数组的交集 II
author: xdkxlk
tags: []
categories:
  - LeetCode
date: 2018-09-28 17:31:00
---
[题目](https://leetcode-cn.com/explore/interview/card/top-interview-questions-easy/1/array/26/)

- 如果给定的数组已经排好序呢？你将如何优化你的算法？
```java
public static int[] intersect1(int[] nums1, int[] nums2) {
        if (nums1.length > nums2.length) {
            return intersect2(nums2, nums1);
        }
        if (nums1.length == 0 || nums2.length == 0) {
            return new int[0];
        }
        Arrays.sort(nums1);
        Arrays.sort(nums2);

        List<Integer> res = new ArrayList<>();
        for (int i = 0, j = 0; i < nums1.length && j < nums2.length; ) {
            if (nums1[i] == nums2[j]) {
                res.add(nums1[i]);
                i++;
                j++;
            } else if (nums1[i] < nums2[j]) {
                //如果nums1的小了，那么换一个大一点的跟nums2比较
                i++;
            } else {
                //如果nums2的小了，那么换一个大一点的跟nums1比较
                j++;
            }
        }
        return res.stream().mapToInt(n -> n).toArray();
    }
```
- 如果 nums2 的元素存储在磁盘上，磁盘内存是有限的，并且你不能一次加载所有的元素到内存中，你该怎么办？
```java
public static int[] intersect(int[] nums1, int[] nums2) {
        List<Integer> res = new ArrayList<>();
        Map<Integer, Integer> numMap = new HashMap<>();
        for (int n : nums1) {
            if (numMap.containsKey(n)) {
                numMap.put(n, numMap.get(n) + 1);
            } else {
                numMap.put(n, 1);
            }
        }
        
        //nums2很大，所以，相当于读取的是一个nums2的流
        Arrays.stream(nums2).forEach(n -> {
            Integer nV = numMap.get(n);
            if (nV != null) {
                if (nV == 0) {
                    numMap.remove(n);
                } else {
                    res.add(n);
                    nV--;
                    numMap.put(n, nV);
                }
            }
        });
        return res.stream().mapToInt(n -> n).toArray();
    }
```