import 'package:flutter/material.dart';

import 'api.dart';

class OperationDialog extends StatefulWidget {
  final BankApi api;
  final List<Map<String, dynamic>> accounts;
  final String kind;
  final String? accountId;
  const OperationDialog({
    super.key,
    required this.api,
    required this.accounts,
    required this.kind,
    this.accountId,
  });
  @override
  State<OperationDialog> createState() => _OperationDialogState();
}

class _OperationDialogState extends State<OperationDialog> {
  final form = GlobalKey<FormState>();
  final amount = TextEditingController(text: '100');
  final currency = TextEditingController(text: 'EUR');
  final reference = TextEditingController();
  final category = TextEditingController(text: 'SUPERMARKET');
  final country = TextEditingController(text: 'FR');
  String? source;
  String? target;
  String? error;
  bool busy = false;
  bool get creating => widget.kind == 'account';
  bool get transfer => widget.kind == 'transfer';
  String get title => switch (widget.kind) {
    'account' => 'Nouveau compte',
    'payment' => 'Nouveau paiement',
    'transfer' => 'Nouveau virement',
    'credit' => 'Créditer le compte',
    _ => 'Débiter le compte',
  };

  @override
  void initState() {
    super.initState();
    source =
        widget.accountId ??
        (widget.accounts.isEmpty ? null : '${widget.accounts.first['id']}');
    if (source != null) selectSource(source!);
    if (creating) amount.text = '1000';
  }

  void selectSource(String id) {
    source = id;
    final account = widget.accounts.firstWhere((a) => a['id'] == id);
    currency.text = '${account['currency']}';
    final candidates = widget.accounts
        .where((a) => a['id'] != source && a['currency'] == currency.text)
        .toList();
    target = candidates.isEmpty ? null : '${candidates.first['id']}';
  }

  @override
  void dispose() {
    for (final c in [amount, currency, reference, category, country]) {
      c.dispose();
    }
    super.dispose();
  }

  Widget field(
    TextEditingController controller,
    String label, {
    bool decimal = false,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextFormField(
      controller: controller,
      enabled: !busy,
      keyboardType: decimal
          ? const TextInputType.numberWithOptions(decimal: true)
          : TextInputType.text,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      validator: (value) {
        if (value == null || value.trim().isEmpty) return 'Champ obligatoire';
        if (decimal && !RegExp(r'^\d+([.,]\d{1,4})?$').hasMatch(value.trim())) {
          return 'Montant positif, quatre décimales maximum';
        }
        return null;
      },
    ),
  );

  Widget picker(
    String label,
    String? value,
    List<Map<String, dynamic>> accounts,
    ValueChanged<String?>? changed,
  ) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: DropdownButtonFormField<String>(
      key: ValueKey('$label-$value'),
      initialValue: value,
      isExpanded: true,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      items: accounts
          .map(
            (a) => DropdownMenuItem(
              value: '${a['id']}',
              child: Text(
                '${a['customerReference']} · ${a['currency']}',
                overflow: TextOverflow.ellipsis,
              ),
            ),
          )
          .toList(),
      onChanged: busy ? null : changed,
      validator: (value) => value == null ? 'Sélectionne un compte' : null,
    ),
  );

  Future<void> submit() async {
    if (!form.currentState!.validate()) return;
    setState(() {
      busy = true;
      error = null;
    });
    final value = amount.text.trim().replaceAll(',', '.');
    final code = currency.text.trim().toUpperCase();
    String path;
    Map<String, dynamic> body;
    if (creating) {
      path = '/api/accounts';
      body = {
        'customerReference': reference.text.trim(),
        'currency': code,
        'balance': value,
      };
    } else if (transfer) {
      path = '/api/transactions/transfers';
      body = {
        'sourceAccountId': source,
        'targetAccountId': target,
        'amount': value,
        'currency': code,
        'country': country.text.trim().toUpperCase(),
      };
    } else if (widget.kind == 'payment') {
      path = '/api/transactions';
      body = {
        'accountId': source,
        'amount': value,
        'currency': code,
        'merchantCategory': category.text.trim(),
        'country': country.text.trim().toUpperCase(),
      };
    } else {
      path = '/api/accounts/$source/${widget.kind}';
      body = {'amount': value, 'currency': code};
    }
    try {
      final data = await widget.api.call('POST', path, body);
      if (!mounted) return;
      Navigator.pop(context, {'path': path, 'data': data});
    } catch (e) {
      if (!mounted) return;
      // An uncertain financial result must be reviewed, never blindly resubmitted.
      if (e is ApiError && e.data is Map && e.data['transactionId'] != null) {
        Navigator.pop(context, {'path': path, 'data': e.data});
        return;
      }
      setState(() {
        busy = false;
        error = e.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(title),
    content: SizedBox(
      width: 440,
      child: SingleChildScrollView(
        child: Form(
          key: form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (creating)
                field(reference, 'Référence client')
              else
                picker(
                  transfer ? 'Compte source' : 'Compte',
                  source,
                  widget.accounts,
                  widget.accountId != null
                      ? null
                      : (id) {
                          if (id != null) setState(() => selectSource(id));
                        },
                ),
              if (transfer)
                picker(
                  'Compte destinataire',
                  target,
                  widget.accounts.where((a) => a['id'] != source).toList(),
                  (id) => setState(() => target = id),
                ),
              field(
                amount,
                creating ? 'Solde initial' : 'Montant',
                decimal: true,
              ),
              field(currency, 'Devise (EUR, MAD…)'),
              if (widget.kind == 'payment')
                field(category, 'Catégorie du commerçant'),
              if (widget.kind == 'payment' || transfer)
                field(country, 'Pays (FR, MA…)'),
              if (error != null)
                Text(
                  error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              if (!creating && widget.kind != 'credit')
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Text(
                    'Le serveur vérifie le compte, la devise et le solde.',
                  ),
                ),
            ],
          ),
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: busy ? null : () => Navigator.pop(context),
        child: const Text('Annuler'),
      ),
      FilledButton(
        onPressed: busy ? null : submit,
        child: Text(busy ? 'En cours…' : 'Confirmer'),
      ),
    ],
  );
}
