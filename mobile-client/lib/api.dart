import 'dart:convert';

import 'package:http/http.dart' as http;

class ApiError implements Exception {
  final String message;
  final dynamic data;
  ApiError(this.message, this.data);
  @override
  String toString() => message;
}

class BankApi {
  String baseUrl;
  BankApi(this.baseUrl);

  Future<dynamic> call(String method, String path, [dynamic body]) async {
    final request = http.Request(method, Uri.parse('$baseUrl$path'));
    request.headers['Content-Type'] = 'application/json';
    if (body != null) request.body = jsonEncode(body);
    final response = await http.Response.fromStream(
      await request.send().timeout(const Duration(seconds: 75)),
    ).timeout(const Duration(seconds: 75));
    dynamic data;
    try {
      data = response.body.isEmpty ? null : jsonDecode(response.body);
    } catch (_) {
      throw ApiError(
        'Réponse du serveur illisible (${response.statusCode}).',
        null,
      );
    }
    if (response.statusCode >= 400) {
      throw ApiError(
        data is Map
            ? '${data['detail'] ?? data['message'] ?? 'Erreur ${response.statusCode}'}'
            : 'Erreur ${response.statusCode}',
        data,
      );
    }
    return data;
  }
}

String pretty(dynamic data) => const JsonEncoder.withIndent('  ').convert(data);
String money(dynamic value) {
  final number = num.tryParse('$value') ?? 0;
  final precision = (number * 100 - (number * 100).round()).abs() < 0.00001
      ? 2
      : 4;
  return number.toStringAsFixed(precision);
}
