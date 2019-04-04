function deleteKey(token, row) {
    return function () {
        apiV2Request('keys', 'DELETE', {key: token}).then(function () {
            row.remove();
        });
    }
}

$(function () {
    $('.api-key-row').each(function () {
        var row = $(this);
        var token = row.find('.api-key-row-token').text();
        row.find('.api-key-row-delete-button').click(deleteKey(token, row));
    });

    $('#button-create-new-key').click(function () {
        var checked = [];
        $('#api-create-key-form').find('input[type=checkbox]').filter("input[id^='perm.']").filter(':checked').each(function () {
            checked.push($(this).attr('id').substr('perm.'.length))
        });
        var name = $('#keyName').val();

        if (checked.length === 0) {
            console.log("No perms checked")
        } else {
            var hasName = name.length !== 0;
            if (!hasName) {
                name = null;
            }

            var data = {
                permissions: checked,
                name: name
            };

            apiV2Request('keys', 'POST', data).then(function (newKey) {
                var namedPerms = '';

                for (let perm of checked) {
                    namedPerms += perm + ", "
                }

                namedPerms.substr(0, namedPerms.length - 2);

                var row = $('<tr>');
                var token = newKey.key;

                row.append($('<th>').text(hasName ? name : '<Unknown name>'));
                row.append($('<th>').addClass('api-key-row-token').text(token));
                row.append($('<th>').text(namedPerms));
                row.append($('<th>').append($('<button>').addClass('btn btn-danger api-key-row-delete-button').text('Delete key').click(deleteKey(token, row))));

                $('#api-key-rows:last-child').append(row);
            })
        }
    });
});